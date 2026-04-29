package org.example.dip2.dto.room;

import java.util.List;

public record CurrentQuestionResponse(
        String id,
        String text,
        String imageUrl,
        int baseWeight,
        Integer timerOverride,
        List<CurrentQuestionAnswerResponse> answers
) {
}
