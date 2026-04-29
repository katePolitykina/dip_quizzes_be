package org.example.dip2.dto.quiz;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record QuizQuestionRequest(
        @NotBlank String text,
        String imageUrl,
        @NotNull @Min(1) Integer pointsWeight,
        @Min(1) Integer timerOverride,
        @Valid @NotEmpty List<QuizAnswerRequest> answers
) {
}
