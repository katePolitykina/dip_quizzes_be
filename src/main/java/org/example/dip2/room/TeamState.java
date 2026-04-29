package org.example.dip2.room;

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
public class TeamState {

    private String teamId;
    private String name;

    @Builder.Default
    private List<String> participantIds = new ArrayList<>();

    private String captainParticipantId;
    private String analystParticipantId;
    private String selectedAnswerId;
    private String confirmedAnswerId;
    private ConfidenceLevel confirmedConfidenceLevel;
    private Long answeredAtEpochMillis;
    private boolean analystPowerUsed;

    @Builder.Default
    private List<String> hiddenAnswerIds = new ArrayList<>();

    @Builder.Default
    private List<TeamQuestionScore> questionScores = new ArrayList<>();

    private double totalScore;
}
