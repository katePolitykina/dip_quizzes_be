package org.example.dip2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

        var finished = roomService.loadSession(pin);
        assertEquals(GameStatus.FINISHED, finished.getStatus());
        assertNotNull(finished.getFinalReport());
        assertEquals(1, finished.getLeaderboard().size());
        assertEquals(1, finished.getTeams().get(0).getQuestionScores().size());
        assertNotNull(roomService.toResponse(finished).finalReport());
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

    private User saveUser(String email) {
        return userRepository.save(User.builder()
                .email(email)
                .password("password")
                .displayName(email)
                .provider(AuthProvider.LOCAL)
                .build());
    }

    private Quiz saveQuiz(User author) {
        Question question = Question.builder()
                .text("Which option is correct?")
                .pointsWeight(100)
                .timerOverride(30)
                .build();
        question.replaceAnswers(List.of(
                Answer.builder().text("Correct").isCorrect(true).build(),
                Answer.builder().text("Wrong A").isCorrect(false).build(),
                Answer.builder().text("Wrong B").isCorrect(false).build(),
                Answer.builder().text("Wrong C").isCorrect(false).build()
        ));

        Quiz quiz = Quiz.builder()
                .title("Engine Quiz")
                .author(author)
                .build();
        quiz.replaceQuestions(List.of(question));
        return quizRepository.saveAndFlush(quiz);
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
