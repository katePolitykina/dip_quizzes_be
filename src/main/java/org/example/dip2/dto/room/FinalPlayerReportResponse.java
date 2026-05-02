package org.example.dip2.dto.room;

public record FinalPlayerReportResponse(
        String participantId,
        String displayName,
        String teamName,
        int correctAnswers,
        long totalResponseTimeMillis,
        double averageResponseTimeMillis,
        int rank
) {
}
