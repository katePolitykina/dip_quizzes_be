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
public class FinalQuestionPlayerAnswer {

    private String participantId;
    private String displayName;
    private String selectedAnswerId;
    private String selectedAnswerText;
    private Long responseTimeMillis;
}
