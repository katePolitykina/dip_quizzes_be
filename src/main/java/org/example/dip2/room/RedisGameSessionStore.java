package org.example.dip2.room;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
import org.example.dip2.config.RoomProperties;
import org.example.dip2.exception.ApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.rooms.store", havingValue = "redis", matchIfMissing = true)
public class RedisGameSessionStore implements GameSessionStore {

    private static final long LOCK_RETRY_SLEEP_MILLIS = 25L;

    private final RedisTemplate<String, GameSession> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final RoomProperties roomProperties;

    public RedisGameSessionStore(
            RedisTemplate<String, GameSession> redisTemplate,
            StringRedisTemplate stringRedisTemplate,
            RoomProperties roomProperties
    ) {
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.roomProperties = roomProperties;
    }

    @Override
    public boolean createIfAbsent(GameSession session) {
        Boolean created = redisTemplate.opsForValue().setIfAbsent(sessionKey(session.getPin()), session, roomProperties.getTtl());
        return Boolean.TRUE.equals(created);
    }

    @Override
    public Optional<GameSession> findByPin(String pin) {
        GameSession session = redisTemplate.opsForValue().get(sessionKey(pin));
        if (session != null) {
            redisTemplate.expire(sessionKey(pin), roomProperties.getTtl());
        }
        return Optional.ofNullable(session);
    }

    @Override
    public GameSession save(GameSession session) {
        redisTemplate.opsForValue().set(sessionKey(session.getPin()), session, roomProperties.getTtl());
        return session;
    }

    @Override
    public <T> T executeLocked(String pin, Supplier<T> action) {
        String lockKey = lockKey(pin);
        Duration lockTimeout = roomProperties.getLockTimeout();
        long deadline = System.nanoTime() + lockTimeout.toNanos();

        while (System.nanoTime() < deadline) {
            Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "locked", lockTimeout);
            if (Boolean.TRUE.equals(locked)) {
                try {
                    return action.get();
                } finally {
                    stringRedisTemplate.delete(lockKey);
                }
            }
            sleepBriefly();
        }

        throw new ApiException(HttpStatus.CONFLICT, "Room is busy, please retry");
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(LOCK_RETRY_SLEEP_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Interrupted while waiting for room lock");
        }
    }

    private String sessionKey(String pin) {
        return "game-session:" + pin;
    }

    private String lockKey(String pin) {
        return "game-session-lock:" + pin;
    }
}
