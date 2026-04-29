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
public class CbmSettings {

    private double highCorrectMultiplier;
    private double mediumCorrectMultiplier;
    private double lowCorrectMultiplier;
    private double highIncorrectPenaltyMultiplier;
    private double mediumIncorrectPenaltyMultiplier;
    private double lowIncorrectPenaltyMultiplier;
}
