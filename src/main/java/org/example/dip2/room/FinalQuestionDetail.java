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
public class FinalQuestionDetail {

    private String questionId;
    private String questionText;

    @Builder.Default
    private List<FinalQuestionPlayerAnswer> playerAnswers = new ArrayList<>();
}
