package org.example.dip2.dto.ws;

import jakarta.validation.constraints.NotNull;

public record ModerationCommandRequest(
        @NotNull ModerationCommand command,
        String targetParticipantId
) {
    public enum ModerationCommand {
        TOGGLE_PAUSE,
        KICK_PLAYER,
        END_GAME
    }
}
