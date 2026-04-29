package org.example.dip2.service;

import org.example.dip2.room.CbmSettings;
import org.example.dip2.room.ConfidenceLevel;
import org.springframework.stereotype.Component;

@Component
public class ScoringUtility {

    public ScoreBreakdown calculateScore(
            int baseWeight,
            boolean correct,
            double speedFactor,
            ConfidenceLevel confidenceLevel,
            CbmSettings cbmSettings
    ) {
        double appliedMultiplier = multiplierFor(correct, confidenceLevel, cbmSettings);
        double effectiveSpeedFactor = correct ? speedFactor : 1.0;
        double awardedPoints = baseWeight * effectiveSpeedFactor * appliedMultiplier;
        return new ScoreBreakdown(awardedPoints, effectiveSpeedFactor, appliedMultiplier);
    }

    private double multiplierFor(boolean correct, ConfidenceLevel confidenceLevel, CbmSettings cbmSettings) {
        return switch (confidenceLevel) {
            case HIGH -> correct ? cbmSettings.getHighCorrectMultiplier() : cbmSettings.getHighIncorrectPenaltyMultiplier();
            case MEDIUM -> correct ? cbmSettings.getMediumCorrectMultiplier() : cbmSettings.getMediumIncorrectPenaltyMultiplier();
            case LOW -> correct ? cbmSettings.getLowCorrectMultiplier() : cbmSettings.getLowIncorrectPenaltyMultiplier();
        };
    }

    public record ScoreBreakdown(double pointsAwarded, double speedFactor, double appliedMultiplier) {
    }
}
