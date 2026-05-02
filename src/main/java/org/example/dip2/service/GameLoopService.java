package org.example.dip2.service;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.example.dip2.dto.room.GameSessionResponse;
import org.example.dip2.dto.ws.AnswerHistogramPayload;
import org.example.dip2.dto.ws.SortAnswersResponse;
import org.example.dip2.dto.ws.StartGameRequest;
import org.example.dip2.dto.ws.TeamAnswerEventPayload;
import org.example.dip2.exception.ApiException;
import org.example.dip2.model.Quiz;
import org.example.dip2.repository.QuizRepository;
import org.example.dip2.room.CbmSettings;
import org.example.dip2.room.ConfidenceLevel;
import org.example.dip2.room.FinalGameReport;
import org.example.dip2.room.FinalTeamReport;
import org.example.dip2.room.GameSession;
import org.example.dip2.room.GameStatus;
import org.example.dip2.room.LeaderboardEntry;
import org.example.dip2.room.PlayerSlot;
import org.example.dip2.room.QuestionAnswerState;
import org.example.dip2.room.QuestionState;
import org.example.dip2.room.TeamQuestionScore;
import org.example.dip2.room.TeamRole;
import org.example.dip2.room.TeamState;
import org.example.dip2.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GameLoopService {

    private final RoomService roomService;
    private final QuizRepository quizRepository;
    private final ScoringUtility scoringUtility;
    private final RoomRealtimeService roomRealtimeService;
    private final ScheduledExecutorService timerExecutor = Executors.newSingleThreadScheduledExecutor();

    public GameLoopService(
            RoomService roomService,
            QuizRepository quizRepository,
            ScoringUtility scoringUtility,
            RoomRealtimeService roomRealtimeService
    ) {
        this.roomService = roomService;
        this.quizRepository = quizRepository;
        this.scoringUtility = scoringUtility;
        this.roomRealtimeService = roomRealtimeService;
    }

    @Transactional(readOnly = true)
    public void startGame(String pin, AuthenticatedUser authenticatedUser, StartGameRequest request) {
        roomService.executeLocked(pin, () -> {
            GameSession session = roomService.loadSession(pin);
            roomService.ensureHost(session, authenticatedUser);
            roomService.ensureLobbyState(session);

            Quiz quiz = quizRepository.findByIdAndAuthorId(UUID.fromString(request.quizId()), authenticatedUser.id())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Quiz not found"));

            session.setQuizId(quiz.getId().toString());
            session.setQuizTitle(quiz.getTitle());
            session.setQuestions(quiz.getQuestions().stream().map(question -> QuestionState.builder()
                    .id(question.getId().toString())
                    .text(question.getText())
                    .imageUrl(question.getImageUrl())
                    .baseWeight(question.getPointsWeight())
                    .timerOverride(question.getTimerOverride())
                    .answers(question.getAnswers().stream().map(answer -> QuestionAnswerState.builder()
                            .id(answer.getId().toString())
                            .text(answer.getText())
                            .correct(Boolean.TRUE.equals(answer.getIsCorrect()))
                            .build()).toList())
                    .build()).toList());

            if (session.getTeams().isEmpty()) {
                throw new ApiException(HttpStatus.CONFLICT, "Teams must be created before starting a game");
            }
            if (session.isPlayInTeams()) {
                boolean hasWaitingParticipants = session.getParticipants().stream()
                        .anyMatch(player -> player.getTeamId() == null);
                if (hasWaitingParticipants) {
                    throw new ApiException(HttpStatus.CONFLICT, "All joined players must be assigned to a team before the game starts");
                }
                boolean hasEmptyTeams = session.getTeams().stream().anyMatch(team -> team.getParticipantIds().isEmpty());
                if (hasEmptyTeams) {
                    throw new ApiException(HttpStatus.CONFLICT, "Every configured team must have at least one participant before the game starts");
                }
                boolean hasIncompleteTeams = session.getTeams().stream()
                        .anyMatch(team -> team.getCaptainParticipantId() == null || team.getAnalystParticipantId() == null);
                if (hasIncompleteTeams) {
                    throw new ApiException(HttpStatus.CONFLICT, "Every team must have both a captain and analyst before the game starts");
                }
            }

            session.setCbmSettings(applyOverrides(session.getCbmSettings(), request.cbmOverrides()));
            resetGameProgress(session);
            startQuestion(session, 0);
            return null;
        });
    }

    public void advanceFromResults(String pin, AuthenticatedUser authenticatedUser) {
        roomService.executeLocked(pin, () -> {
            GameSession session = roomService.loadSession(pin);
            roomService.ensureHost(session, authenticatedUser);
            if (session.getStatus() != GameStatus.SHOW_RESULTS) {
                throw new ApiException(HttpStatus.CONFLICT, "Room is not currently showing results");
            }
            int nextIndex = session.getCurrentQuestionIndex() + 1;
            if (nextIndex >= session.getQuestions().size()) {
                throw new ApiException(HttpStatus.CONFLICT, "No more questions remain");
            }
            startQuestion(session, nextIndex);
            return null;
        });
    }

    public void selectTeamAnswer(String pin, String teamId, AuthenticatedUser authenticatedUser, String answerId) {
        roomService.executeLocked(pin, () -> {
            GameSession session = roomService.loadSession(pin);
            ensureQuestionActive(session);
            TeamState team = roomService.loadTeam(session, teamId);
            PlayerSlot participant = roomService.loadParticipant(session, authenticatedUser.id().toString());
            ensureParticipantInTeam(team, participant);
            validateAnswerBelongsToQuestion(session, answerId);

            participant.setSelectedAnswerId(answerId);
            if (participant.getParticipantId().equals(team.getCaptainParticipantId())) {
                team.setSelectedAnswerId(answerId);
            }
            roomService.saveAndBroadcast(session);
            roomRealtimeService.broadcastTeamSelection(
                    session.getPin(),
                    teamId,
                    new TeamAnswerEventPayload(teamId, participant.getParticipantId(), participant.getSelectedAnswerId(), team.getConfirmedAnswerId(), false, null)
            );
            return null;
        });
    }

    public void confirmTeamAnswer(String pin, String teamId, AuthenticatedUser authenticatedUser, String answerId, String confidenceLevelValue) {
        roomService.executeLocked(pin, () -> {
            GameSession session = roomService.loadSession(pin);
            ensureQuestionActive(session);
            TeamState team = roomService.loadTeam(session, teamId);
            PlayerSlot participant = roomService.loadParticipant(session, authenticatedUser.id().toString());
            ensureParticipantInTeam(team, participant);
            if (!participant.getParticipantId().equals(team.getCaptainParticipantId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Only the captain can confirm a team answer");
            }
            validateAnswerBelongsToQuestion(session, answerId);
            boolean confirmedCorrect = currentQuestion(session).getAnswers().stream()
                    .filter(answer -> answer.getId().equals(answerId))
                    .findFirst()
                    .map(QuestionAnswerState::isCorrect)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Confirmed answer does not belong to current question"));

            team.setSelectedAnswerId(answerId);
            team.setConfirmedAnswerId(answerId);
            team.setConfirmedConfidenceLevel(parseConfidence(confidenceLevelValue));
            team.setAnsweredAtEpochMillis(System.currentTimeMillis());
            participant.setLastAnsweredAtEpochMillis(team.getAnsweredAtEpochMillis());
            recalculateAnswersCount(session);

            GameSessionResponse response = roomService.saveAndBroadcast(session);
            roomRealtimeService.broadcastTeamSelection(
                    session.getPin(),
                    teamId,
                    new TeamAnswerEventPayload(teamId, participant.getParticipantId(), team.getSelectedAnswerId(), team.getConfirmedAnswerId(), true, confirmedCorrect)
            );
            roomRealtimeService.broadcastRoomEvent(
                    session.getPin(),
                    "ANSWER_HISTOGRAM",
                    new AnswerHistogramPayload(session.getPin(), response.answersCount(), session.getTeams().size())
            );
            if (response.answersCount() == session.getTeams().size()) {
                finalizeQuestion(session.getPin(), session.getCurrentQuestionIndex(), session.getQuestionStartedAt());
            }
            return null;
        });
    }

    public void useSortAnswers(String pin, String teamId, AuthenticatedUser authenticatedUser) {
        roomService.executeLocked(pin, () -> {
            GameSession session = roomService.loadSession(pin);
            ensureQuestionActive(session);
            TeamState team = roomService.loadTeam(session, teamId);
            PlayerSlot participant = roomService.loadParticipant(session, authenticatedUser.id().toString());
            ensureParticipantInTeam(team, participant);
            boolean canUseSortAnswers = participant.getParticipantId().equals(team.getAnalystParticipantId())
                    || (!session.isPlayInTeams() && participant.getParticipantId().equals(team.getCaptainParticipantId()));
            if (!canUseSortAnswers) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Only the analyst can use SORT_ANSWERS");
            }
            if (team.isAnalystPowerUsed()) {
                throw new ApiException(HttpStatus.CONFLICT, "SORT_ANSWERS has already been used for this team");
            }

            List<String> hiddenAnswerIds = currentQuestion(session).getAnswers().stream()
                    .filter(answer -> !answer.isCorrect())
                    .limit(2)
                    .map(QuestionAnswerState::getId)
                    .toList();

            team.setAnalystPowerUsed(true);
            team.setHiddenAnswerIds(new ArrayList<>(hiddenAnswerIds));
            roomService.saveAndBroadcast(session);
            roomRealtimeService.broadcastTeamEvent(
                    session.getPin(),
                    teamId,
                    "ANALYST_SORT_ANSWERS",
                    new SortAnswersResponse(teamId, hiddenAnswerIds)
            );
            return null;
        });
    }

    public void togglePause(String pin, AuthenticatedUser authenticatedUser) {
        roomService.executeLocked(pin, () -> {
            GameSession session = roomService.loadSession(pin);
            roomService.ensureHost(session, authenticatedUser);
            if (session.getStatus() == GameStatus.FINISHED) {
                throw new ApiException(HttpStatus.CONFLICT, "Finished rooms cannot be paused");
            }

            boolean paused = session.getStatus() != GameStatus.PAUSED;
            if (paused) {
                session.setStatusBeforePause(session.getStatus());
                session.setPausedAt(Instant.now());
                session.setStatus(GameStatus.PAUSED);
            } else {
                Instant pausedAt = session.getPausedAt();
                GameStatus resumeStatus = session.getStatusBeforePause() == null ? GameStatus.LOBBY : session.getStatusBeforePause();
                if (pausedAt != null && resumeStatus == GameStatus.START_QUESTION && session.getQuestionDeadlineAt() != null) {
                    long pauseMillis = Math.max(0L, Instant.now().toEpochMilli() - pausedAt.toEpochMilli());
                    session.setQuestionDeadlineAt(session.getQuestionDeadlineAt().plusMillis(pauseMillis));
                    scheduleQuestionTimeout(
                            session.getPin(),
                            session.getCurrentQuestionIndex(),
                            session.getQuestionStartedAt(),
                            Math.max(1L, (session.getQuestionDeadlineAt().toEpochMilli() - Instant.now().toEpochMilli()) / 1000L)
                    );
                }
                session.setStatus(resumeStatus);
                session.setStatusBeforePause(null);
                session.setPausedAt(null);
            }

            session.setUpdatedAt(Instant.now());
            GameSessionResponse response = roomService.saveAndBroadcast(session);
            roomRealtimeService.broadcastRoomEvent(session.getPin(), paused ? "GAME_PAUSED" : "GAME_RESUMED", response);
            return null;
        });
    }

    public void endGame(String pin, AuthenticatedUser authenticatedUser) {
        roomService.executeLocked(pin, () -> {
            GameSession session = roomService.loadSession(pin);
            roomService.ensureHost(session, authenticatedUser);
            if (session.getStatus() == GameStatus.FINISHED) {
                return null;
            }

            recalculateLeaderboard(session);
            session.setStatus(GameStatus.FINISHED);
            session.setPausedAt(null);
            session.setStatusBeforePause(null);
            session.setQuestionDeadlineAt(null);
            session.setFinalReport(session.getQuizId() == null ? null : buildFinalReport(session));
            session.setUpdatedAt(Instant.now());

            GameSessionResponse response = roomService.saveAndBroadcast(session);
            roomRealtimeService.broadcastRoomEvent(session.getPin(), "LEADERBOARD_UPDATED", response.leaderboard());
            roomRealtimeService.broadcastLeaderboard(session.getPin(), response.leaderboard());
            if (response.finalReport() != null) {
                roomRealtimeService.broadcastRoomEvent(session.getPin(), "FINAL_REPORT", response.finalReport());
            }
            return null;
        });
    }

    @PreDestroy
    void shutdownScheduler() {
        timerExecutor.shutdownNow();
    }

    private void startQuestion(GameSession session, int questionIndex) {
        session.setStatus(GameStatus.START_QUESTION);
        session.setCurrentQuestionIndex(questionIndex);
        session.setQuestionStartedAt(Instant.now());
        long timerSeconds = questionTimerSeconds(session, questionIndex);
        session.setQuestionDeadlineAt(session.getQuestionStartedAt().plusSeconds(timerSeconds));
        session.setAnswersCount(0);
        for (TeamState team : session.getTeams()) {
            team.setSelectedAnswerId(null);
            team.setConfirmedAnswerId(null);
            team.setConfirmedConfidenceLevel(null);
            team.setAnsweredAtEpochMillis(null);
            team.setHiddenAnswerIds(new ArrayList<>());
        }
        for (PlayerSlot participant : session.getParticipants()) {
            participant.setSelectedAnswerId(null);
        }
        session.setUpdatedAt(Instant.now());

        roomService.saveAndBroadcast(session);
        roomRealtimeService.broadcastRoomEvent(session.getPin(), "QUESTION_STARTED", roomService.toResponse(session));
        scheduleQuestionTimeout(session.getPin(), questionIndex, session.getQuestionStartedAt(), timerSeconds);
    }

    private void scheduleQuestionTimeout(String pin, int questionIndex, Instant questionStartedAt, long timerSeconds) {
        timerExecutor.schedule(() -> finalizeQuestion(pin, questionIndex, questionStartedAt), timerSeconds, TimeUnit.SECONDS);
    }

    private void finalizeQuestion(String pin, Integer questionIndex, Instant startedAt) {
        roomService.executeLocked(pin, () -> {
            GameSession session = roomService.loadSession(pin);
            if (session.getStatus() != GameStatus.START_QUESTION
                    || session.getCurrentQuestionIndex() == null
                    || !session.getCurrentQuestionIndex().equals(questionIndex)
                    || session.getQuestionStartedAt() == null
                    || !session.getQuestionStartedAt().equals(startedAt)) {
                return null;
            }

            QuestionState question = currentQuestion(session);
            long durationSeconds = questionTimerSeconds(session, session.getCurrentQuestionIndex());
            long durationMillis = durationSeconds * 1000L;
            long deadlineMillis = session.getQuestionDeadlineAt().toEpochMilli();

            for (TeamState team : session.getTeams()) {
                TeamQuestionScore score = buildQuestionScore(session, team, question, durationMillis, deadlineMillis);
                team.getQuestionScores().add(score);
                team.setTotalScore(team.getTotalScore() + score.getPointsAwarded());
            }

            recalculateLeaderboard(session);
            session.setStatus(isLastQuestion(session) ? GameStatus.FINISHED : GameStatus.SHOW_RESULTS);
            session.setUpdatedAt(Instant.now());
            if (session.getStatus() == GameStatus.FINISHED) {
                session.setFinalReport(buildFinalReport(session));
            }

            GameSessionResponse response = roomService.saveAndBroadcast(session);
            roomRealtimeService.broadcastRoomEvent(session.getPin(), "QUESTION_RESULTS", response);
            roomRealtimeService.broadcastRoomEvent(session.getPin(), "LEADERBOARD_UPDATED", response.leaderboard());
            roomRealtimeService.broadcastLeaderboard(session.getPin(), response.leaderboard());
            if (session.getStatus() == GameStatus.FINISHED) {
                roomRealtimeService.broadcastRoomEvent(session.getPin(), "FINAL_REPORT", response.finalReport());
            }
            return null;
        });
    }

    private TeamQuestionScore buildQuestionScore(
            GameSession session,
            TeamState team,
            QuestionState question,
            long durationMillis,
            long deadlineMillis
    ) {
        if (team.getConfirmedAnswerId() == null || team.getConfirmedConfidenceLevel() == null) {
            return TeamQuestionScore.builder()
                    .questionId(question.getId())
                    .selectedAnswerId(team.getSelectedAnswerId())
                    .confidenceLevel(null)
                    .correct(false)
                    .speedFactor(1.0)
                    .appliedMultiplier(0.0)
                    .pointsAwarded(0.0)
                    .build();
        }

        QuestionAnswerState selectedAnswer = question.getAnswers().stream()
                .filter(answer -> answer.getId().equals(team.getConfirmedAnswerId()))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Confirmed answer does not belong to current question"));

        long answeredAt = team.getAnsweredAtEpochMillis() == null ? deadlineMillis : team.getAnsweredAtEpochMillis();
        double remainingRatio = durationMillis <= 0 ? 0.0 : Math.max(0.0, (double) (deadlineMillis - answeredAt) / durationMillis);
        double speedFactor = 1.0 + remainingRatio;
        ScoringUtility.ScoreBreakdown breakdown = scoringUtility.calculateScore(
                question.getBaseWeight(),
                selectedAnswer.isCorrect(),
                speedFactor,
                team.getConfirmedConfidenceLevel(),
                session.getCbmSettings() == null ? roomService.defaultCbmSettings() : session.getCbmSettings()
        );

        return TeamQuestionScore.builder()
                .questionId(question.getId())
                .selectedAnswerId(team.getConfirmedAnswerId())
                .confidenceLevel(team.getConfirmedConfidenceLevel())
                .correct(selectedAnswer.isCorrect())
                .speedFactor(breakdown.speedFactor())
                .appliedMultiplier(breakdown.appliedMultiplier())
                .pointsAwarded(breakdown.pointsAwarded())
                .build();
    }

    private void recalculateLeaderboard(GameSession session) {
        List<LeaderboardEntry> leaderboard = session.getTeams().stream()
                .map(team -> LeaderboardEntry.builder()
                        .teamId(team.getTeamId())
                        .teamName(team.getName())
                        .score(team.getTotalScore())
                        .build())
                .sorted(Comparator.comparingDouble(LeaderboardEntry::getScore).reversed())
                .toList();

        List<LeaderboardEntry> ranked = new ArrayList<>();
        for (int index = 0; index < leaderboard.size(); index++) {
            LeaderboardEntry entry = leaderboard.get(index);
            entry.setRank(index + 1);
            ranked.add(entry);
        }
        session.setLeaderboard(ranked);
    }

    private FinalGameReport buildFinalReport(GameSession session) {
        return FinalGameReport.builder()
                .quizId(session.getQuizId())
                .quizTitle(session.getQuizTitle())
                .generatedAt(Instant.now())
                .teams(session.getTeams().stream()
                        .map(team -> FinalTeamReport.builder()
                                .teamId(team.getTeamId())
                                .teamName(team.getName())
                                .totalScore(team.getTotalScore())
                                .questionScores(new ArrayList<>(team.getQuestionScores()))
                                .build())
                        .toList())
                .build();
    }

    private void resetGameProgress(GameSession session) {
        session.setCurrentQuestionIndex(null);
        session.setQuestionStartedAt(null);
        session.setQuestionDeadlineAt(null);
        session.setAnswersCount(0);
        session.setPausedAt(null);
        session.setStatusBeforePause(null);
        session.setLeaderboard(new ArrayList<>());
        session.setFinalReport(null);
        for (TeamState team : session.getTeams()) {
            team.setSelectedAnswerId(null);
            team.setConfirmedAnswerId(null);
            team.setConfirmedConfidenceLevel(null);
            team.setAnsweredAtEpochMillis(null);
            team.setAnalystPowerUsed(false);
            team.setHiddenAnswerIds(new ArrayList<>());
            team.setQuestionScores(new ArrayList<>());
            team.setTotalScore(0.0);
        }
        for (PlayerSlot participant : session.getParticipants()) {
            participant.setLastAnsweredAtEpochMillis(null);
        }
    }

    private void recalculateAnswersCount(GameSession session) {
        session.setAnswersCount((int) session.getTeams().stream().filter(team -> team.getConfirmedAnswerId() != null).count());
    }

    private void validateAnswerBelongsToQuestion(GameSession session, String answerId) {
        boolean matches = currentQuestion(session).getAnswers().stream().anyMatch(answer -> answer.getId().equals(answerId));
        if (!matches) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Answer does not belong to the current question");
        }
    }

    private void ensureQuestionActive(GameSession session) {
        if (session.getStatus() != GameStatus.START_QUESTION) {
            throw new ApiException(HttpStatus.CONFLICT, "No active question is currently running");
        }
    }

    private void ensureParticipantInTeam(TeamState team, PlayerSlot participant) {
        if (!team.getParticipantIds().contains(participant.getParticipantId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Participant is not part of this team");
        }
    }

    private ConfidenceLevel parseConfidence(String confidenceLevelValue) {
        if (confidenceLevelValue == null || confidenceLevelValue.isBlank()) {
            return ConfidenceLevel.LOW;
        }
        try {
            return ConfidenceLevel.valueOf(confidenceLevelValue.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported confidence level");
        }
    }

    private QuestionState currentQuestion(GameSession session) {
        if (session.getCurrentQuestionIndex() == null || session.getCurrentQuestionIndex() < 0 || session.getCurrentQuestionIndex() >= session.getQuestions().size()) {
            throw new ApiException(HttpStatus.CONFLICT, "Current question is not available");
        }
        return session.getQuestions().get(session.getCurrentQuestionIndex());
    }

    private boolean isLastQuestion(GameSession session) {
        return session.getCurrentQuestionIndex() != null && session.getCurrentQuestionIndex() >= session.getQuestions().size() - 1;
    }

    private long questionTimerSeconds(GameSession session, int questionIndex) {
        Integer override = session.getQuestions().get(questionIndex).getTimerOverride();
        return override == null ? session.getGlobalTimer() : override;
    }

    private CbmSettings applyOverrides(CbmSettings original, StartGameRequest.CbmOverrideRequest overrides) {
        CbmSettings base = original == null ? roomService.defaultCbmSettings() : original;
        if (overrides == null) {
            return base;
        }
        return CbmSettings.builder()
                .highCorrectMultiplier(overrides.highCorrectMultiplier() == null ? base.getHighCorrectMultiplier() : overrides.highCorrectMultiplier())
                .mediumCorrectMultiplier(overrides.mediumCorrectMultiplier() == null ? base.getMediumCorrectMultiplier() : overrides.mediumCorrectMultiplier())
                .lowCorrectMultiplier(overrides.lowCorrectMultiplier() == null ? base.getLowCorrectMultiplier() : overrides.lowCorrectMultiplier())
                .highIncorrectPenaltyMultiplier(overrides.highIncorrectPenaltyMultiplier() == null ? base.getHighIncorrectPenaltyMultiplier() : overrides.highIncorrectPenaltyMultiplier())
                .mediumIncorrectPenaltyMultiplier(overrides.mediumIncorrectPenaltyMultiplier() == null ? base.getMediumIncorrectPenaltyMultiplier() : overrides.mediumIncorrectPenaltyMultiplier())
                .lowIncorrectPenaltyMultiplier(overrides.lowIncorrectPenaltyMultiplier() == null ? base.getLowIncorrectPenaltyMultiplier() : overrides.lowIncorrectPenaltyMultiplier())
                .build();
    }
}
