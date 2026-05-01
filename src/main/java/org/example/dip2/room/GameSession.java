package org.example.dip2.room;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameSession {

    private String pin;
    private String hostUserId;
    private int globalTimer;
    private boolean cbmEnabled;
    private boolean playInTeams;
    private Integer configuredTeamCount;
    private GameStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private String quizId;
    private String quizTitle;
    private Integer currentQuestionIndex;
    private Instant questionStartedAt;
    private Instant questionDeadlineAt;
    private int answersCount;
    private Instant pausedAt;
    private GameStatus statusBeforePause;
    private CbmSettings cbmSettings;

    @Builder.Default
    private List<PlayerSlot> participants = new ArrayList<>();

    @Builder.Default
    private List<TeamState> teams = new ArrayList<>();

    @Builder.Default
    private List<QuestionState> questions = new ArrayList<>();

    @Builder.Default
    private List<LeaderboardEntry> leaderboard = new ArrayList<>();

    private FinalGameReport finalReport;
}
