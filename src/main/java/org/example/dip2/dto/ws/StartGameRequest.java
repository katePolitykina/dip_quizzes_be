package org.example.dip2.dto.ws;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record StartGameRequest(
        @NotBlank String quizId,
        @Valid CbmOverrideRequest cbmOverrides
) {
    public record CbmOverrideRequest(
            Double highCorrectMultiplier,
            Double mediumCorrectMultiplier,
            Double lowCorrectMultiplier,
            Double highIncorrectPenaltyMultiplier,
            Double mediumIncorrectPenaltyMultiplier,
            Double lowIncorrectPenaltyMultiplier
    ) {
    }
}
