package org.example.dip2.websocket;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

@Component
public class WebSocketSessionRegistry {

    private final ConcurrentHashMap<String, WebSocketSession> nativeSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionToParticipant = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> participantToSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> sessionToRooms = new ConcurrentHashMap<>();

    public void registerNativeSession(WebSocketSession session) {
        nativeSessions.put(session.getId(), session);
    }

    public void registerAuthenticatedSession(String sessionId, String participantId) {
        sessionToParticipant.put(sessionId, participantId);
        participantToSessions.computeIfAbsent(participantId, ignored -> ConcurrentHashMap.newKeySet()).add(sessionId);
    }

    public void registerRoomSubscription(String sessionId, String roomPin) {
        sessionToRooms.computeIfAbsent(sessionId, ignored -> ConcurrentHashMap.newKeySet()).add(roomPin);
    }

    public void unregisterSession(String sessionId) {
        nativeSessions.remove(sessionId);
        Set<String> rooms = sessionToRooms.remove(sessionId);
        if (rooms != null) {
            rooms.clear();
        }
        String participantId = sessionToParticipant.remove(sessionId);
        if (participantId != null) {
            Set<String> sessions = participantToSessions.get(participantId);
            if (sessions != null) {
                sessions.remove(sessionId);
                if (sessions.isEmpty()) {
                    participantToSessions.remove(participantId);
                }
            }
        }
    }

    public void disconnectParticipant(String roomPin, String participantId) {
        Set<String> sessionIds = participantToSessions.getOrDefault(participantId, Set.of());
        for (String sessionId : sessionIds) {
            Set<String> subscribedRooms = sessionToRooms.getOrDefault(sessionId, Set.of());
            if (subscribedRooms.isEmpty() || subscribedRooms.contains(roomPin)) {
                WebSocketSession session = nativeSessions.get(sessionId);
                if (session != null && session.isOpen()) {
                    try {
                        session.close(CloseStatus.POLICY_VIOLATION);
                    } catch (IOException ignored) {
                        // Best-effort close; the session will be cleaned up on disconnect callbacks.
                    }
                }
                unregisterSession(sessionId);
            }
        }
    }
}
