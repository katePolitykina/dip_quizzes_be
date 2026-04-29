package org.example.dip2.dto.room;

import java.util.List;

public record TeamStateResponse(
        String teamId,
        String name,
        List<String> participantIds,
        String captainParticipantId,
        String analystParticipantId,
        String confirmedConfidenceLevel,
        String selectedAnswerId,
        String confirmedAnswerId,
        double totalScore,
        boolean analystPowerUsed
) {
}
