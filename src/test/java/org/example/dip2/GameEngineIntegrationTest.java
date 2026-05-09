package org.example.dip2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.example.dip2.model.Answer;
import org.example.dip2.model.AuthProvider;
import org.example.dip2.model.Question;
import org.example.dip2.model.Quiz;
import org.example.dip2.model.User;
import org.example.dip2.repository.QuizRepository;
import org.example.dip2.repository.UserRepository;
import org.example.dip2.room.GameStatus;
import org.example.dip2.security.AuthenticatedUser;
import org.example.dip2.service.GameLoopService;
import org.example.dip2.service.RoomService;
import org.example.dip2.exception.ApiException;
import org.example.dip2.dto.room.AutoDistributeTeamsRequest;
import org.example.dip2.dto.room.CreateRoomRequest;
import org.example.dip2.dto.ws.StartGameRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class GameEngineIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private RoomService roomService;

    @Autowired
    private GameLoopService gameLoopService;

    @Test
    void gameFinalizesWithLeaderboardAndFinalReport() {
        User hostUser = saveUser("host-engine@example.com");
        User captainUser = saveUser("captain-engine@example.com");
        User analystUser = saveUser("analyst-engine@example.com");
        Quiz quiz = saveQuiz(hostUser);

        AuthenticatedUser host = authenticatedUser(hostUser, "Host");
        AuthenticatedUser captain = authenticatedUser(captainUser, "Captain");
        AuthenticatedUser analyst = authenticatedUser(analystUser, "Analyst");

        String pin = roomService.createRoom(host, new CreateRoomRequest(30, true, true, 2)).pin();
        roomService.joinRoom(pin, captain);
        roomService.joinRoom(pin, analyst);
        roomService.autoDistribute(pin, host, new AutoDistributeTeamsRequest(1));
        gameLoopService.startGame(pin, host, new StartGameRequest(quiz.getId().toString(), null));

        String teamId = roomService.loadSession(pin).getTeams().get(0).getTeamId();
        String correctAnswerId = roomService.loadSession(pin).getQuestions().get(0).getAnswers().stream()
                .filter(answer -> answer.isCorrect())
                .findFirst()
                .orElseThrow()
                .getId();

        gameLoopService.selectTeamAnswer(pin, teamId, analyst, correctAnswerId);
        gameLoopService.confirmTeamAnswer(pin, teamId, captain, correctAnswerId, "HIGH");

        var active = roomService.loadSession(pin);
        assertEquals(GameStatus.START_QUESTION, active.getStatus());
        assertNull(active.getFinalReport());
        assertEquals(0, active.getTeams().get(0).getQuestionScores().size());
        assertEquals(1, roomService.loadParticipant(active, analystUser.getId().toString()).getQuestionAnswers().size());
        assertEquals(correctAnswerId, roomService.loadParticipant(active, analystUser.getId().toString()).getQuestionAnswers().get(0).getSelectedAnswerId());
        assertNotNull(roomService.loadParticipant(active, analystUser.getId().toString()).getQuestionAnswers().get(0).getResponseTimeMillis());
    }

    @Test
    void timerExpiryMovesRoomToNextQuestion() {
        User hostUser = saveUser("host-timer@example.com");
        User captainUser = saveUser("captain-timer@example.com");
        User analystUser = saveUser("analyst-timer@example.com");
        Quiz quiz = saveQuiz(hostUser, 1, 2);

        AuthenticatedUser host = authenticatedUser(hostUser, "Host");
        AuthenticatedUser captain = authenticatedUser(captainUser, "Captain");
        AuthenticatedUser analyst = authenticatedUser(analystUser, "Analyst");

        String pin = roomService.createRoom(host, new CreateRoomRequest(30, true, true, 2)).pin();
        roomService.joinRoom(pin, captain);
        roomService.joinRoom(pin, analyst);
        roomService.autoDistribute(pin, host, new AutoDistributeTeamsRequest(1));
        gameLoopService.startGame(pin, host, new StartGameRequest(quiz.getId().toString(), null));

        String teamId = roomService.loadSession(pin).getTeams().get(0).getTeamId();
        String correctAnswerId = roomService.loadSession(pin).getQuestions().get(0).getAnswers().stream()
                .filter(answer -> answer.isCorrect())
                .findFirst()
                .orElseThrow()
                .getId();

        gameLoopService.selectTeamAnswer(pin, teamId, analyst, correctAnswerId);
        gameLoopService.confirmTeamAnswer(pin, teamId, captain, correctAnswerId, "HIGH");

        waitForStatus(pin, GameStatus.START_QUESTION, 1);

        var nextQuestion = roomService.loadSession(pin);
        assertEquals(GameStatus.START_QUESTION, nextQuestion.getStatus());
        assertEquals(1, nextQuestion.getCurrentQuestionIndex());
        assertEquals(1, nextQuestion.getTeams().get(0).getQuestionScores().size());
        assertNull(nextQuestion.getFinalReport());
        assertEquals(1, roomService.loadParticipant(nextQuestion, analystUser.getId().toString()).getQuestionAnswers().size());
        assertEquals(correctAnswerId, roomService.loadParticipant(nextQuestion, analystUser.getId().toString()).getQuestionAnswers().get(0).getSelectedAnswerId());
    }

    @Test
    void finalTimerExpiryFinalizesWithLeaderboardAndFinalReport() {
        User hostUser = saveUser("host-final@example.com");
        User captainUser = saveUser("captain-final@example.com");
        User analystUser = saveUser("analyst-final@example.com");
        Quiz quiz = saveQuiz(hostUser, 1, 1);

        AuthenticatedUser host = authenticatedUser(hostUser, "Host");
        AuthenticatedUser captain = authenticatedUser(captainUser, "Captain");
        AuthenticatedUser analyst = authenticatedUser(analystUser, "Analyst");

        String pin = roomService.createRoom(host, new CreateRoomRequest(30, true, true, 2)).pin();
        roomService.joinRoom(pin, captain);
        roomService.joinRoom(pin, analyst);
        roomService.autoDistribute(pin, host, new AutoDistributeTeamsRequest(1));
        gameLoopService.startGame(pin, host, new StartGameRequest(quiz.getId().toString(), null));

        String teamId = roomService.loadSession(pin).getTeams().get(0).getTeamId();
        String correctAnswerId = roomService.loadSession(pin).getQuestions().get(0).getAnswers().stream()
                .filter(answer -> answer.isCorrect())
                .findFirst()
                .orElseThrow()
                .getId();

        gameLoopService.selectTeamAnswer(pin, teamId, analyst, correctAnswerId);
        gameLoopService.confirmTeamAnswer(pin, teamId, captain, correctAnswerId, "HIGH");

        waitForStatus(pin, GameStatus.FINISHED, 1);

        var finished = roomService.loadSession(pin);
        assertEquals(GameStatus.FINISHED, finished.getStatus());
        assertNotNull(finished.getFinalReport());
        assertEquals(1, finished.getLeaderboard().size());
        assertEquals(1, finished.getTeams().get(0).getQuestionScores().size());
        assertNotNull(roomService.toResponse(finished).finalReport());
        assertEquals(1, roomService.loadParticipant(finished, captainUser.getId().toString()).getQuestionAnswers().size());
        assertEquals(correctAnswerId, roomService.loadParticipant(finished, captainUser.getId().toString()).getQuestionAnswers().get(0).getSelectedAnswerId());
    }

    @Test
    void analystPowerCanOnlyBeUsedOncePerGamePerTeam() {
        User hostUser = saveUser("host-power@example.com");
        User captainUser = saveUser("captain-power@example.com");
        User analystUser = saveUser("analyst-power@example.com");
        Quiz quiz = saveQuiz(hostUser);

        AuthenticatedUser host = authenticatedUser(hostUser, "Host");
        AuthenticatedUser captain = authenticatedUser(captainUser, "Captain");
        AuthenticatedUser analyst = authenticatedUser(analystUser, "Analyst");

        String pin = roomService.createRoom(host, new CreateRoomRequest(30, true, true, 2)).pin();
        roomService.joinRoom(pin, captain);
        roomService.joinRoom(pin, analyst);
        roomService.autoDistribute(pin, host, new AutoDistributeTeamsRequest(1));
        gameLoopService.startGame(pin, host, new StartGameRequest(quiz.getId().toString(), null));

        String teamId = roomService.loadSession(pin).getTeams().get(0).getTeamId();
        gameLoopService.useSortAnswers(pin, teamId, analyst);

        assertThrows(ApiException.class, () -> gameLoopService.useSortAnswers(pin, teamId, analyst));
        assertEquals(2, roomService.loadSession(pin).getTeams().get(0).getHiddenAnswerIds().size());
    }

    @Test
    void playersCanSeeWhetherTheirOwnSelectedAnswerWasCorrectAfterCaptainConfirms() {
        User hostUser = saveUser("host-member-answer@example.com");
        User captainUser = saveUser("captain-member-answer@example.com");
        User analystUser = saveUser("analyst-member-answer@example.com");
        Quiz quiz = saveQuiz(hostUser);

        AuthenticatedUser host = authenticatedUser(hostUser, "Host");
        AuthenticatedUser captain = authenticatedUser(captainUser, "Captain");
        AuthenticatedUser analyst = authenticatedUser(analystUser, "Analyst");

        String pin = roomService.createRoom(host, new CreateRoomRequest(30, true, true, 2)).pin();
        roomService.joinRoom(pin, captain);
        roomService.joinRoom(pin, analyst);
        roomService.autoDistribute(pin, host, new AutoDistributeTeamsRequest(1));
        gameLoopService.startGame(pin, host, new StartGameRequest(quiz.getId().toString(), null));

        var session = roomService.loadSession(pin);
        String teamId = session.getTeams().get(0).getTeamId();
        String correctAnswerId = session.getQuestions().get(0).getAnswers().stream()
                .filter(answer -> answer.isCorrect())
                .findFirst()
                .orElseThrow()
                .getId();
        String wrongAnswerId = session.getQuestions().get(0).getAnswers().stream()
                .filter(answer -> !answer.isCorrect())
                .findFirst()
                .orElseThrow()
                .getId();

        gameLoopService.selectTeamAnswer(pin, teamId, analyst, correctAnswerId);
        gameLoopService.selectTeamAnswer(pin, teamId, captain, wrongAnswerId);
        gameLoopService.confirmTeamAnswer(pin, teamId, captain, wrongAnswerId, "HIGH");

        var response = roomService.toResponse(roomService.loadSession(pin));
        var analystResponse = response.participants().stream()
                .filter(player -> player.participantId().equals(analystUser.getId().toString()))
                .findFirst()
                .orElseThrow();
        var captainResponse = response.participants().stream()
                .filter(player -> player.participantId().equals(captainUser.getId().toString()))
                .findFirst()
                .orElseThrow();

        assertEquals(correctAnswerId, analystResponse.selectedAnswerId());
        assertTrue(Boolean.TRUE.equals(analystResponse.selectedAnswerCorrect()));
        assertEquals(wrongAnswerId, captainResponse.selectedAnswerId());
        assertTrue(Boolean.FALSE.equals(captainResponse.selectedAnswerCorrect()));
    }

    @Test
    void hostCanEndActiveGameForAllPlayers() {
        User hostUser = saveUser("host-end@example.com");
        User captainUser = saveUser("captain-end@example.com");
        User analystUser = saveUser("analyst-end@example.com");
        Quiz quiz = saveQuiz(hostUser);

        AuthenticatedUser host = authenticatedUser(hostUser, "Host");
        AuthenticatedUser captain = authenticatedUser(captainUser, "Captain");
        AuthenticatedUser analyst = authenticatedUser(analystUser, "Analyst");

        String pin = roomService.createRoom(host, new CreateRoomRequest(30, true, true, 2)).pin();
        roomService.joinRoom(pin, captain);
        roomService.joinRoom(pin, analyst);
        roomService.autoDistribute(pin, host, new AutoDistributeTeamsRequest(1));
        gameLoopService.startGame(pin, host, new StartGameRequest(quiz.getId().toString(), null));

        gameLoopService.endGame(pin, host);

        var finished = roomService.loadSession(pin);
        assertEquals(GameStatus.FINISHED, finished.getStatus());
        assertNotNull(finished.getFinalReport());
        assertNotNull(roomService.toResponse(finished).finalReport());
    }

    private User saveUser(String email) {
        return userRepository.save(User.builder()
                .email(email)
                .password("password")
                .displayName(email)
                .provider(AuthProvider.LOCAL)
                .build());
    }

    private Quiz saveQuiz(User author) {
        return saveQuiz(author, 30, 1);
    }

    private Quiz saveQuiz(User author, int timerOverrideSeconds, int questionCount) {
        Quiz quiz = Quiz.builder()
                .title("Engine Quiz")
                .author(author)
                .build();

        quiz.replaceQuestions(java.util.stream.IntStream.range(0, questionCount)
                .mapToObj(index -> {
                    Question question = Question.builder()
                            .text("Which option is correct? " + index)
                            .pointsWeight(100)
                            .timerOverride(timerOverrideSeconds)
                            .build();
                    question.replaceAnswers(List.of(
                            Answer.builder().text("Correct").isCorrect(true).build(),
                            Answer.builder().text("Wrong A").isCorrect(false).build(),
                            Answer.builder().text("Wrong B").isCorrect(false).build(),
                            Answer.builder().text("Wrong C").isCorrect(false).build()
                    ));
                    return question;
                }).toList());
        return quizRepository.saveAndFlush(quiz);
    }

    private void waitForStatus(String pin, GameStatus expectedStatus, int expectedQuestionIndex) {
        long deadline = System.currentTimeMillis() + 4000L;
        while (System.currentTimeMillis() < deadline) {
            var session = roomService.loadSession(pin);
            if (session.getStatus() == expectedStatus && session.getCurrentQuestionIndex() != null
                    && session.getCurrentQuestionIndex() == expectedQuestionIndex) {
                return;
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for room state", exception);
            }
        }
        var session = roomService.loadSession(pin);
        throw new AssertionError("Expected status %s at question %s but was %s at %s".formatted(
                expectedStatus,
                expectedQuestionIndex,
                session.getStatus(),
                session.getCurrentQuestionIndex()
        ));
    }

    private AuthenticatedUser authenticatedUser(User user, String displayName) {
        return AuthenticatedUser.builder()
                .id(user.getId())
                .email(user.getEmail())
                .displayName(displayName)
                .provider("LOCAL")
                .role("ROLE_USER")
                .build();
    }
}
