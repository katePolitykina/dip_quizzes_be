package org.example.dip2.dto.quiz;

import java.time.Instant;
import java.util.List;

public record QuizDetailResponse(
        String id,
        String title,
        String authorId,
        Instant createdAt,
        Instant updatedAt,
        List<QuizQuestionResponse> questions
) {
}
