package org.example.dip2.dto.room;

public record FinalQuestionPlayerAnswerResponse(
        String participantId,
        String displayName,
        String selectedAnswerId,
        String selectedAnswerText,
        Long responseTimeMillis
) {
}
