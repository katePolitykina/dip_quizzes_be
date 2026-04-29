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
public class FinalTeamReport {

    private String teamId;
    private String teamName;
    private double totalScore;

    @Builder.Default
    private List<TeamQuestionScore> questionScores = new ArrayList<>();
}
