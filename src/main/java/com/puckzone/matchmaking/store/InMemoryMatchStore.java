package com.puckzone.matchmaking.store;

import com.puckzone.matchmaking.config.MatchmakingProperties;
import com.puckzone.matchmaking.model.Match;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Matches pendientes en memoria (desarrollo local y tests). La expiración
 * es perezosa: se filtra al leer y se barre al escribir — suficiente para
 * el volumen de una máquina de desarrollo.
 */
public class InMemoryMatchStore implements MatchStore {

    private final Map<String, Match> matchesByUser = new ConcurrentHashMap<>();
    private final MatchmakingProperties properties;

    public InMemoryMatchStore(MatchmakingProperties properties) {
        this.properties = properties;
    }

    @Override
    public void put(String userId, Match match) {
        evictExpired();
        matchesByUser.put(userId, match);
    }

    @Override
    public Optional<Match> find(String userId) {
        return Optional.ofNullable(matchesByUser.get(userId)).filter(m -> !isExpired(m));
    }

    @Override
    public boolean discard(String userId) {
        return matchesByUser.remove(userId) != null;
    }

    private boolean isExpired(Match match) {
        return match.createdAt().plus(properties.matchRetention()).isBefore(Instant.now());
    }

    private void evictExpired() {
        matchesByUser.values().removeIf(this::isExpired);
    }
}
