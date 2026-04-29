package org.example.dip2.room;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.example.dip2.config.RoomProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.rooms.store", havingValue = "memory")
public class InMemoryGameSessionStore implements GameSessionStore {

    private final Map<String, GameSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Object> locks = new ConcurrentHashMap<>();
    private final RoomProperties roomProperties;

    public InMemoryGameSessionStore(RoomProperties roomProperties) {
        this.roomProperties = roomProperties;
    }

    @Override
    public boolean createIfAbsent(GameSession session) {
        cleanupExpired(session.getPin());
        return sessions.putIfAbsent(session.getPin(), session) == null;
    }

    @Override
    public Optional<GameSession> findByPin(String pin) {
        cleanupExpired(pin);
        GameSession session = sessions.get(pin);
        if (session != null) {
            session.setUpdatedAt(Instant.now());
            sessions.put(pin, session);
        }
        return Optional.ofNullable(session);
    }

    @Override
    public GameSession save(GameSession session) {
        sessions.put(session.getPin(), session);
        return session;
    }

    @Override
    public <T> T executeLocked(String pin, Supplier<T> action) {
        Object lock = locks.computeIfAbsent(pin, ignored -> new Object());
        synchronized (lock) {
            return action.get();
        }
    }

    private void cleanupExpired(String pin) {
        GameSession session = sessions.get(pin);
        if (session == null || session.getUpdatedAt() == null) {
            return;
        }
        Instant expiresAt = session.getUpdatedAt().plus(roomProperties.getTtl());
        if (Instant.now().isAfter(expiresAt)) {
            sessions.remove(pin);
        }
    }
}
