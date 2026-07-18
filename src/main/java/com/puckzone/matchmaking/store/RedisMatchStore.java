package com.puckzone.matchmaking.store;

import com.puckzone.matchmaking.config.MatchmakingProperties;
import com.puckzone.matchmaking.model.Match;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

/**
 * Matches pendientes en Redis: {@code mm:match:{userId}} → Match en JSON,
 * con TTL = {@code match-retention} — la expiración que antes hacía el
 * barrido del servicio ahora la hace Redis solo.
 */
public class RedisMatchStore implements MatchStore {

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;
    private final MatchmakingProperties properties;

    public RedisMatchStore(StringRedisTemplate redis, JsonMapper jsonMapper,
                           MatchmakingProperties properties) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
        this.properties = properties;
    }

    @Override
    public void put(String userId, Match match) {
        redis.opsForValue().set(key(userId), jsonMapper.writeValueAsString(match),
                properties.matchRetention());
    }

    @Override
    public Optional<Match> find(String userId) {
        return Optional.ofNullable(redis.opsForValue().get(key(userId)))
                .map(json -> jsonMapper.readValue(json, Match.class));
    }

    @Override
    public boolean discard(String userId) {
        return Boolean.TRUE.equals(redis.delete(key(userId)));
    }

    private String key(String userId) {
        return "mm:match:" + userId;
    }
}
