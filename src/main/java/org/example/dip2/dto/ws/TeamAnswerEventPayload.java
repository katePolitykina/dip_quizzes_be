package org.example.dip2.dto.ws;

public record TeamAnswerEventPayload(
        String teamId,
        String participantId,
        String selectedAnswerId,
        String confirmedAnswerId,
        boolean finalized
) {
}
