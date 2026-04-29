package org.example.dip2.dto.quiz;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record QuizUpsertRequest(
        @NotBlank @Size(min = 3, max = 100) String title,
        @Valid @NotEmpty List<QuizQuestionRequest> questions
) {
}
