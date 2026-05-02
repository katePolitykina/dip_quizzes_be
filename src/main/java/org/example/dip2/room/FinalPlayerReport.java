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
public class FinalPlayerReport {

    private String participantId;
    private String displayName;
    private String teamName;
    private int correctAnswers;
    private long totalResponseTimeMillis;
    private double averageResponseTimeMillis;
    private int rank;
}
