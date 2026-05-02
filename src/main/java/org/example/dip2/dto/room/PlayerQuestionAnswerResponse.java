package org.example.dip2.dto.room;

public record PlayerQuestionAnswerResponse(
        String questionId,
        Integer questionIndex,
        String selectedAnswerId,
        Long answeredAtEpochMillis,
        Long responseTimeMillis
) {
}
