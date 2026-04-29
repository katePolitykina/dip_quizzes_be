package org.example.dip2.service;

import java.util.List;
import org.example.dip2.dto.room.GameSessionResponse;
import org.example.dip2.dto.room.LeaderboardEntryResponse;
import org.example.dip2.dto.ws.SocketEnvelope;
import org.example.dip2.dto.ws.TeamAnswerEventPayload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class RoomRealtimeService {

    private final SimpMessagingTemplate simpMessagingTemplate;

    public RoomRealtimeService(SimpMessagingTemplate simpMessagingTemplate) {
        this.simpMessagingTemplate = simpMessagingTemplate;
    }

    public void broadcastRoomState(GameSessionResponse session) {
        simpMessagingTemplate.convertAndSend("/topic/rooms/" + session.pin(), new SocketEnvelope("ROOM_STATE", session));
    }

    public void broadcastRoomEvent(String pin, String type, Object payload) {
        simpMessagingTemplate.convertAndSend("/topic/rooms/" + pin, new SocketEnvelope(type, payload));
    }

    public void broadcastTeamSelection(String pin, String teamId, TeamAnswerEventPayload payload) {
        simpMessagingTemplate.convertAndSend(
                "/topic/rooms/" + pin + "/teams/" + teamId,
                new SocketEnvelope(payload.finalized() ? "TEAM_ANSWER_CONFIRMED" : "TEAM_SELECTION_UPDATED", payload)
        );
    }

    public void broadcastLeaderboard(String pin, List<LeaderboardEntryResponse> leaderboard) {
        simpMessagingTemplate.convertAndSend(
                "/topic/rooms/" + pin + "/leaderboard",
                new SocketEnvelope("LEADERBOARD_UPDATED", leaderboard)
        );
    }

    public void broadcastTeamEvent(String pin, String teamId, String type, Object payload) {
        simpMessagingTemplate.convertAndSend(
                "/topic/rooms/" + pin + "/teams/" + teamId,
                new SocketEnvelope(type, payload)
        );
    }
}
