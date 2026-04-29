package org.example.dip2.room;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamQuestionScore {

    private String questionId;
    private String selectedAnswerId;
    private ConfidenceLevel confidenceLevel;
    private boolean correct;
    private double speedFactor;
    private double appliedMultiplier;
    private double pointsAwarded;
}
