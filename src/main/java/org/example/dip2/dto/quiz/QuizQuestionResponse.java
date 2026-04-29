package org.example.dip2.dto.quiz;

import java.util.List;

public record QuizQuestionResponse(
        String id,
        String text,
        String imageUrl,
        Integer pointsWeight,
        Integer timerOverride,
        List<QuizAnswerResponse> answers
) {
}
