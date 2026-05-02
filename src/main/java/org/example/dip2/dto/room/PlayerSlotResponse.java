package org.example.dip2.dto.room;

public record PlayerSlotResponse(
        String participantId,
        String userId,
        String displayName,
        String avatarUrl,
        String provider,
        boolean guest,
        String teamId,
        String teamRole,
        String selectedAnswerId,
        java.util.List<PlayerQuestionAnswerResponse> questionAnswers
) {
}
