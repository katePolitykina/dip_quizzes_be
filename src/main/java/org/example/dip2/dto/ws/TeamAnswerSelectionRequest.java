package org.example.dip2.dto.ws;

import jakarta.validation.constraints.NotBlank;

public record TeamAnswerSelectionRequest(
        @NotBlank String answerId,
        String confidenceLevel
) {
}
