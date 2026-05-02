package org.example.dip2.dto.room;

import java.util.List;

public record FinalQuestionDetailResponse(
        String questionId,
        String questionText,
        List<FinalQuestionPlayerAnswerResponse> playerAnswers
) {
}
