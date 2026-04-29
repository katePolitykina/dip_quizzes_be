package org.example.dip2.dto.ws;

public record AnswerHistogramPayload(
        String pin,
        int answersCount,
        int totalTeams
) {
}
