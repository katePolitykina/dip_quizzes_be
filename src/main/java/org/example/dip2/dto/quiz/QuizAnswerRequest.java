package org.example.dip2.dto.quiz;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record QuizAnswerRequest(
        @NotBlank String text,
        @NotNull Boolean isCorrect
) {
}
