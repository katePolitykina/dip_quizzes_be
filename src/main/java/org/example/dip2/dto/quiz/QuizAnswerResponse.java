package org.example.dip2.dto.quiz;

public record QuizAnswerResponse(
        String id,
        String text,
        boolean isCorrect
) {
}
