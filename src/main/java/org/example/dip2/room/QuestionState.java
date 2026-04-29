package org.example.dip2.room;

import java.util.ArrayList;
import java.util.List;
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
public class QuestionState {

    private String id;
    private String text;
    private String imageUrl;
    private int baseWeight;
    private Integer timerOverride;

    @Builder.Default
    private List<QuestionAnswerState> answers = new ArrayList<>();
}
