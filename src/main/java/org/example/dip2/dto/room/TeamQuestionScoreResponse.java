package org.example.dip2.dto.room;

public record TeamQuestionScoreResponse(
        String questionId,
        String selectedAnswerId,
        String confidenceLevel,
        boolean correct,
        double speedFactor,
        double appliedMultiplier,
        double pointsAwarded
) {
}
