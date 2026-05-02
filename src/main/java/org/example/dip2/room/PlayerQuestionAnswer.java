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
public class PlayerQuestionAnswer {

    private String questionId;
    private Integer questionIndex;
    private String selectedAnswerId;
    private Long answeredAtEpochMillis;
    private Long responseTimeMillis;
}
