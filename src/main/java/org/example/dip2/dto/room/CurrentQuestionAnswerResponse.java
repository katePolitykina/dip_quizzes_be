package org.example.dip2.dto.room;

public record CurrentQuestionAnswerResponse(
        String id,
        String text,
        Boolean correct
) {
}
