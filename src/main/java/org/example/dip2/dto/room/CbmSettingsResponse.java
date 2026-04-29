package org.example.dip2.dto.room;

public record CbmSettingsResponse(
        double highCorrectMultiplier,
        double mediumCorrectMultiplier,
        double lowCorrectMultiplier,
        double highIncorrectPenaltyMultiplier,
        double mediumIncorrectPenaltyMultiplier,
        double lowIncorrectPenaltyMultiplier
) {
}
