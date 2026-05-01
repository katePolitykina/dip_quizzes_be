package org.example.dip2.dto.room;

import java.util.List;
import java.util.Map;

public record TeamStateResponse(
        String teamId,
        String name,
        List<String> participantIds,
        String captainParticipantId,
        String analystParticipantId,
        String confirmedConfidenceLevel,
        String selectedAnswerId,
        String confirmedAnswerId,
        Boolean confirmedAnswerCorrect,
        Map<String, Integer> answerVoteCounts,
        double totalScore,
        boolean analystPowerUsed
) {
}
