package org.example.dip2.dto.ws;

import java.util.List;

public record SortAnswersResponse(
        String teamId,
        List<String> hiddenAnswerIds
) {
}
