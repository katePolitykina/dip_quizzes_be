package org.example.dip2.room;

import java.util.Optional;
import java.util.function.Supplier;

public interface GameSessionStore {

    boolean createIfAbsent(GameSession session);

    Optional<GameSession> findByPin(String pin);

    GameSession save(GameSession session);

    <T> T executeLocked(String pin, Supplier<T> action);
}
