package org.example.dip2.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.example.dip2.config.RoomProperties;
import org.example.dip2.dto.room.AutoDistributeTeamsRequest;
import org.example.dip2.dto.room.CbmSettingsResponse;
import org.example.dip2.dto.room.CreateRoomRequest;
import org.example.dip2.dto.room.CurrentQuestionAnswerResponse;
import org.example.dip2.dto.room.CurrentQuestionResponse;
import org.example.dip2.dto.room.FinalGameReportResponse;
import org.example.dip2.dto.room.FinalTeamReportResponse;
import org.example.dip2.dto.room.GameSessionResponse;
import org.example.dip2.dto.room.LeaderboardEntryResponse;
import org.example.dip2.dto.room.PlayerSlotResponse;
import org.example.dip2.dto.room.TeamQuestionScoreResponse;
import org.example.dip2.dto.room.TeamStateResponse;
import org.example.dip2.dto.room.UpdateTeamRolesRequest;
import org.example.dip2.exception.ApiException;
import org.example.dip2.room.CbmSettings;
import org.example.dip2.room.FinalGameReport;
import org.example.dip2.room.GameSession;
import org.example.dip2.room.GameSessionStore;
import org.example.dip2.room.GameStatus;
import org.example.dip2.room.PlayerSlot;
import org.example.dip2.room.QuestionState;
import org.example.dip2.room.TeamRole;
import org.example.dip2.room.TeamState;
import org.example.dip2.security.AuthenticatedUser;
import org.example.dip2.websocket.WebSocketSessionRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class RoomService {

    private static final String PIN_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int MAX_PIN_ATTEMPTS = 64;

    private final SecureRandom secureRandom = new SecureRandom();
    private final GameSessionStore gameSessionStore;
    private final RoomProperties roomProperties;
    private final RoomRealtimeService roomRealtimeService;
    private final WebSocketSessionRegistry webSocketSessionRegistry;

    public RoomService(
            GameSessionStore gameSessionStore,
            RoomProperties roomProperties,
            RoomRealtimeService roomRealtimeService,
            WebSocketSessionRegistry webSocketSessionRegistry
    ) {
        this.gameSessionStore = gameSessionStore;
        this.roomProperties = roomProperties;
        this.roomRealtimeService = roomRealtimeService;
        this.webSocketSessionRegistry = webSocketSessionRegistry;
    }

    public GameSessionResponse createRoom(AuthenticatedUser authenticatedUser, CreateRoomRequest request) {
        for (int attempt = 0; attempt < MAX_PIN_ATTEMPTS; attempt++) {
            Instant now = Instant.now();
            GameSession session = GameSession.builder()
                    .pin(generatePin(roomProperties.getPinLength()))
                    .hostUserId(authenticatedUser.id().toString())
                    .globalTimer(request.globalTimer())
                    .cbmEnabled(Boolean.TRUE.equals(request.cbmEnabled()))
                    .status(GameStatus.LOBBY)
                    .createdAt(now)
                    .updatedAt(now)
                    .cbmSettings(defaultCbmSettings())
                    .participants(new ArrayList<>(List.of(toPlayerSlot(authenticatedUser))))
                    .teams(new ArrayList<>())
                    .build();
            if (gameSessionStore.createIfAbsent(session)) {
                return broadcastRoomState(session);
            }
        }
        throw new ApiException(HttpStatus.CONFLICT, "Unable to allocate a unique room PIN");
    }

    public GameSessionResponse joinRoom(String pin, AuthenticatedUser authenticatedUser) {
        return executeLocked(pin, () -> {
            GameSession session = loadSession(pin);
            ensureLobbyState(session);

            boolean alreadyJoined = session.getParticipants().stream()
                    .anyMatch(player -> player.getParticipantId().equals(authenticatedUser.id().toString()));
            if (!alreadyJoined) {
                session.getParticipants().add(toPlayerSlot(authenticatedUser));
            }
            session.setUpdatedAt(Instant.now());
            return saveAndBroadcast(session);
        });
    }

    public GameSessionResponse autoDistribute(String pin, AuthenticatedUser authenticatedUser, AutoDistributeTeamsRequest request) {
        return executeLocked(pin, () -> {
            GameSession session = loadSession(pin);
            ensureHost(session, authenticatedUser);
            ensureLobbyState(session);

            int teamCount = request.teamCount();
            if (session.getParticipants().size() < teamCount * 2) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "At least two participants per team are required to assign captain and analyst roles"
                );
            }

            List<PlayerSlot> participants = session.getParticipants().stream()
                    .sorted(Comparator.comparingLong(PlayerSlot::getJoinedAtEpochMillis))
                    .toList();

            List<TeamState> teams = new ArrayList<>();
            for (int index = 0; index < teamCount; index++) {
                teams.add(TeamState.builder()
                        .teamId(UUID.randomUUID().toString())
                        .name("Team " + (index + 1))
                        .participantIds(new ArrayList<>())
                        .build());
            }

            for (int index = 0; index < participants.size(); index++) {
                PlayerSlot participant = participants.get(index);
                TeamState team = teams.get(index % teamCount);
                participant.setTeamId(team.getTeamId());
                participant.setTeamRole(TeamRole.MEMBER);
                team.getParticipantIds().add(participant.getParticipantId());
            }

            for (TeamState team : teams) {
                assignDefaultRoles(session.getParticipants(), team);
            }

            session.setTeams(teams);
            session.setUpdatedAt(Instant.now());
            return saveAndBroadcast(session);
        });
    }

    public GameSessionResponse updateRoles(String pin, AuthenticatedUser authenticatedUser, UpdateTeamRolesRequest request) {
        return executeLocked(pin, () -> {
            GameSession session = loadSession(pin);
            ensureHost(session, authenticatedUser);
            ensureLobbyState(session);

            if (session.getTeams().isEmpty()) {
                throw new ApiException(HttpStatus.CONFLICT, "Teams must be distributed before assigning roles");
            }

            Map<String, UpdateTeamRolesRequest.TeamRoleAssignmentRequest> assignmentsByTeam = request.assignments().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            UpdateTeamRolesRequest.TeamRoleAssignmentRequest::teamId,
                            assignment -> assignment,
                            (left, right) -> right
                    ));

            for (TeamState team : session.getTeams()) {
                UpdateTeamRolesRequest.TeamRoleAssignmentRequest assignment = assignmentsByTeam.get(team.getTeamId());
                if (assignment == null) {
                    continue;
                }
                if (assignment.captainParticipantId().equals(assignment.analystParticipantId())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Captain and analyst must be different participants");
                }
                if (!team.getParticipantIds().contains(assignment.captainParticipantId())
                        || !team.getParticipantIds().contains(assignment.analystParticipantId())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Assigned roles must belong to the target team");
                }
                team.setCaptainParticipantId(assignment.captainParticipantId());
                team.setAnalystParticipantId(assignment.analystParticipantId());
            }

            for (PlayerSlot participant : session.getParticipants()) {
                if (participant.getTeamId() != null) {
                    participant.setTeamRole(TeamRole.MEMBER);
                }
            }
            for (TeamState team : session.getTeams()) {
                applyRole(session.getParticipants(), team.getCaptainParticipantId(), TeamRole.CAPTAIN);
                applyRole(session.getParticipants(), team.getAnalystParticipantId(), TeamRole.ANALYST);
            }

            session.setUpdatedAt(Instant.now());
            return saveAndBroadcast(session);
        });
    }

    public void togglePause(String pin, AuthenticatedUser authenticatedUser) {
        executeLocked(pin, () -> {
            GameSession session = loadSession(pin);
            ensureHost(session, authenticatedUser);
            if (session.getStatus() == GameStatus.FINISHED) {
                throw new ApiException(HttpStatus.CONFLICT, "Finished rooms cannot be paused");
            }

            boolean paused = session.getStatus() != GameStatus.PAUSED;
            if (paused) {
                session.setStatusBeforePause(session.getStatus());
                session.setPausedAt(Instant.now());
                session.setStatus(GameStatus.PAUSED);
            } else {
                session.setStatus(session.getStatusBeforePause() == null ? GameStatus.LOBBY : session.getStatusBeforePause());
                session.setStatusBeforePause(null);
                session.setPausedAt(null);
            }

            session.setUpdatedAt(Instant.now());
            GameSessionResponse response = saveAndBroadcast(session);
            roomRealtimeService.broadcastRoomEvent(session.getPin(), paused ? "GAME_PAUSED" : "GAME_RESUMED", response);
            return null;
        });
    }

    public void kickPlayer(String pin, AuthenticatedUser authenticatedUser, String targetParticipantId) {
        if (targetParticipantId == null || targetParticipantId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Target participant is required");
        }

        executeLocked(pin, () -> {
            GameSession session = loadSession(pin);
            ensureHost(session, authenticatedUser);
            if (session.getHostUserId().equals(targetParticipantId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Host cannot kick themselves");
            }

            boolean removed = session.getParticipants().removeIf(player -> player.getParticipantId().equals(targetParticipantId));
            if (!removed) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Participant not found in room");
            }

            normalizeTeamsAfterMembershipChange(session);
            session.setUpdatedAt(Instant.now());
            GameSessionResponse response = saveAndBroadcast(session);
            webSocketSessionRegistry.disconnectParticipant(session.getPin(), targetParticipantId);
            roomRealtimeService.broadcastRoomEvent(session.getPin(), "PLAYER_KICKED", response);
            return null;
        });
    }

    public <T> T executeLocked(String pin, java.util.function.Supplier<T> action) {
        return gameSessionStore.executeLocked(pin.toUpperCase(), action);
    }

    public GameSession loadSession(String pin) {
        return gameSessionStore.findByPin(pin.toUpperCase())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Room not found"));
    }

    public void ensureHost(GameSession session, AuthenticatedUser authenticatedUser) {
        if (!session.getHostUserId().equals(authenticatedUser.id().toString())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the room host can manage this session");
        }
    }

    public void ensureLobbyState(GameSession session) {
        if (session.getStatus() != GameStatus.LOBBY) {
            throw new ApiException(HttpStatus.CONFLICT, "Room is not in lobby state");
        }
    }

    public TeamState loadTeam(GameSession session, String teamId) {
        return session.getTeams().stream()
                .filter(team -> team.getTeamId().equals(teamId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Team not found"));
    }

    public PlayerSlot loadParticipant(GameSession session, String participantId) {
        return session.getParticipants().stream()
                .filter(player -> player.getParticipantId().equals(participantId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Participant not found in room"));
    }

    public GameSessionResponse saveAndBroadcast(GameSession session) {
        return broadcastRoomState(gameSessionStore.save(session));
    }

    public GameSessionResponse broadcastRoomState(GameSession session) {
        GameSessionResponse response = toResponse(session);
        roomRealtimeService.broadcastRoomState(response);
        return response;
    }

    public GameSessionResponse toResponse(GameSession session) {
        return new GameSessionResponse(
                session.getPin(),
                session.getHostUserId(),
                session.getQuizId(),
                session.getQuizTitle(),
                session.getGlobalTimer(),
                session.isCbmEnabled(),
                session.getStatus().name(),
                session.getCreatedAt(),
                session.getUpdatedAt(),
                session.getCurrentQuestionIndex(),
                session.getQuestionStartedAt(),
                session.getQuestionDeadlineAt(),
                session.getAnswersCount(),
                toCbmSettingsResponse(session.getCbmSettings()),
                toCurrentQuestionResponse(session),
                session.getLeaderboard().stream()
                        .map(entry -> new LeaderboardEntryResponse(entry.getTeamId(), entry.getTeamName(), entry.getScore(), entry.getRank()))
                        .toList(),
                toFinalReportResponse(session.getFinalReport()),
                session.getParticipants().stream().map(player -> new PlayerSlotResponse(
                        player.getParticipantId(),
                        player.getUserId(),
                        player.getDisplayName(),
                        player.getAvatarUrl(),
                        player.getProvider(),
                        player.isGuest(),
                        player.getTeamId(),
                        player.getTeamRole() == null ? null : player.getTeamRole().name()
                )).toList(),
                session.getTeams().stream().map(team -> new TeamStateResponse(
                        team.getTeamId(),
                        team.getName(),
                        List.copyOf(team.getParticipantIds()),
                        team.getCaptainParticipantId(),
                        team.getAnalystParticipantId(),
                        team.getConfirmedConfidenceLevel() == null ? null : team.getConfirmedConfidenceLevel().name(),
                        team.getSelectedAnswerId(),
                        team.getConfirmedAnswerId(),
                        team.getTotalScore(),
                        team.isAnalystPowerUsed()
                )).toList()
        );
    }

    public CbmSettings defaultCbmSettings() {
        return CbmSettings.builder()
                .highCorrectMultiplier(2.0)
                .mediumCorrectMultiplier(1.5)
                .lowCorrectMultiplier(1.0)
                .highIncorrectPenaltyMultiplier(-1.0)
                .mediumIncorrectPenaltyMultiplier(-0.5)
                .lowIncorrectPenaltyMultiplier(0.0)
                .build();
    }

    public void assignDefaultRoles(List<PlayerSlot> participants, TeamState team) {
        List<String> ids = team.getParticipantIds();
        team.setCaptainParticipantId(ids.isEmpty() ? null : ids.get(0));
        team.setAnalystParticipantId(ids.size() > 1 ? ids.get(1) : null);
        applyRole(participants, team.getCaptainParticipantId(), TeamRole.CAPTAIN);
        applyRole(participants, team.getAnalystParticipantId(), TeamRole.ANALYST);
    }

    private void applyRole(List<PlayerSlot> participants, String participantId, TeamRole teamRole) {
        if (participantId == null) {
            return;
        }
        participants.stream()
                .filter(player -> player.getParticipantId().equals(participantId))
                .findFirst()
                .ifPresent(player -> player.setTeamRole(teamRole));
    }

    private void normalizeTeamsAfterMembershipChange(GameSession session) {
        for (PlayerSlot participant : session.getParticipants()) {
            participant.setTeamRole(participant.getTeamId() == null ? null : TeamRole.MEMBER);
        }

        List<TeamState> nonEmptyTeams = new ArrayList<>();
        for (TeamState team : session.getTeams()) {
            team.getParticipantIds().removeIf(participantId ->
                    session.getParticipants().stream().noneMatch(player -> player.getParticipantId().equals(participantId)));
            if (team.getParticipantIds().isEmpty()) {
                continue;
            }
            assignDefaultRoles(session.getParticipants(), team);
            nonEmptyTeams.add(team);
        }
        session.setTeams(nonEmptyTeams);
    }

    private PlayerSlot toPlayerSlot(AuthenticatedUser authenticatedUser) {
        return PlayerSlot.builder()
                .participantId(authenticatedUser.id().toString())
                .userId(authenticatedUser.isGuest() ? null : authenticatedUser.id().toString())
                .displayName(authenticatedUser.displayName())
                .avatarUrl(authenticatedUser.avatarUrl())
                .provider(authenticatedUser.provider())
                .guest(authenticatedUser.isGuest())
                .joinedAtEpochMillis(System.currentTimeMillis())
                .build();
    }

    private String generatePin(int pinLength) {
        StringBuilder builder = new StringBuilder(pinLength);
        for (int index = 0; index < pinLength; index++) {
            builder.append(PIN_ALPHABET.charAt(secureRandom.nextInt(PIN_ALPHABET.length())));
        }
        return builder.toString();
    }

    private CbmSettingsResponse toCbmSettingsResponse(CbmSettings cbmSettings) {
        if (cbmSettings == null) {
            return null;
        }
        return new CbmSettingsResponse(
                cbmSettings.getHighCorrectMultiplier(),
                cbmSettings.getMediumCorrectMultiplier(),
                cbmSettings.getLowCorrectMultiplier(),
                cbmSettings.getHighIncorrectPenaltyMultiplier(),
                cbmSettings.getMediumIncorrectPenaltyMultiplier(),
                cbmSettings.getLowIncorrectPenaltyMultiplier()
        );
    }

    private CurrentQuestionResponse toCurrentQuestionResponse(GameSession session) {
        if (session.getCurrentQuestionIndex() == null
                || session.getQuestions() == null
                || session.getCurrentQuestionIndex() < 0
                || session.getCurrentQuestionIndex() >= session.getQuestions().size()) {
            return null;
        }
        QuestionState question = session.getQuestions().get(session.getCurrentQuestionIndex());
        return new CurrentQuestionResponse(
                question.getId(),
                question.getText(),
                question.getImageUrl(),
                question.getBaseWeight(),
                question.getTimerOverride(),
                question.getAnswers().stream()
                        .map(answer -> new CurrentQuestionAnswerResponse(answer.getId(), answer.getText()))
                        .toList()
        );
    }

    private FinalGameReportResponse toFinalReportResponse(FinalGameReport finalReport) {
        if (finalReport == null) {
            return null;
        }
        return new FinalGameReportResponse(
                finalReport.getQuizId(),
                finalReport.getQuizTitle(),
                finalReport.getGeneratedAt(),
                finalReport.getTeams().stream()
                        .map(team -> new FinalTeamReportResponse(
                                team.getTeamId(),
                                team.getTeamName(),
                                team.getTotalScore(),
                                team.getQuestionScores().stream()
                                        .map(score -> new TeamQuestionScoreResponse(
                                                score.getQuestionId(),
                                                score.getSelectedAnswerId(),
                                                score.getConfidenceLevel() == null ? null : score.getConfidenceLevel().name(),
                                                score.isCorrect(),
                                                score.getSpeedFactor(),
                                                score.getAppliedMultiplier(),
                                                score.getPointsAwarded()
                                        )).toList()
                        )).toList()
        );
    }
}
