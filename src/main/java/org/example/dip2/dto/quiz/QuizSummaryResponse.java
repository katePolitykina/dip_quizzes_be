package org.example.dip2.dto.quiz;

import java.time.Instant;

public record QuizSummaryResponse(
        String id,
        String title,
        int questionCount,
        Instant createdAt,
        Instant updatedAt
) {
}
