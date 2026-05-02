package org.example.dip2.dto.room;

import java.time.Instant;
import java.util.List;

public record FinalGameReportResponse(
        String quizId,
        String quizTitle,
        Instant generatedAt,
        List<FinalPlayerReportResponse> players,
        List<FinalTeamReportResponse> teams
) {
}
