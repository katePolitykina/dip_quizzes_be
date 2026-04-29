package org.example.dip2.dto.room;

import java.util.List;

public record FinalTeamReportResponse(
        String teamId,
        String teamName,
        double totalScore,
        List<TeamQuestionScoreResponse> questionScores
) {
}
