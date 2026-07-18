package com.puckzone.matchmaking.store;

import com.puckzone.matchmaking.config.MatchmakingProperties;
import com.puckzone.matchmaking.model.PrivateRoom;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

/**
 * Salas privadas en Redis: {@code mm:room:{code}} → sala en JSON y
 * {@code mm:room-host:{userId}} → código, ambas con TTL =
 * {@code private-room-ttl} (no hay barrido: Redis las expira solo).
 *
 * <p>El consumo atómico del código usa GETDEL: si dos invitados digitan el
 * mismo código a la vez, Redis le entrega la sala exactamente a uno.
 */
public class RedisPrivateRoomStore implements PrivateRoomStore {

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;
    private final MatchmakingProperties properties;

    public RedisPrivateRoomStore(StringRedisTemplate redis, JsonMapper jsonMapper,
                                 MatchmakingProperties properties) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
        this.properties = properties;
    }

    @Override
    public void save(PrivateRoom room) {
        redis.opsForValue().set(roomKey(room.code()), jsonMapper.writeValueAsString(room),
                properties.privateRoomTtl());
        redis.opsForValue().set(hostKey(room.host().userId()), room.code(),
                properties.privateRoomTtl());
    }

    @Override
    public Optional<PrivateRoom> byCode(String code) {
        return Optional.ofNullable(redis.opsForValue().get(roomKey(code)))
                .map(json -> jsonMapper.readValue(json, PrivateRoom.class));
    }

    @Override
    public Optional<PrivateRoom> consume(String code) {
        Optional<PrivateRoom> room = Optional.ofNullable(redis.opsForValue().getAndDelete(roomKey(code)))
                .map(json -> jsonMapper.readValue(json, PrivateRoom.class));
        // El índice del anfitrión solo se toca si aún apunta a ESTE código
        // (pudo haber creado una sala nueva justo después).
        room.ifPresent(r -> {
            String current = redis.opsForValue().get(hostKey(r.host().userId()));
            if (code.equals(current)) {
                redis.delete(hostKey(r.host().userId()));
            }
        });
        return room;
    }

    @Override
    public Optional<String> codeOf(String hostId) {
        return Optional.ofNullable(redis.opsForValue().get(hostKey(hostId)))
                .filter(code -> byCode(code).isPresent());
    }

    @Override
    public void cancel(String hostId) {
        String code = redis.opsForValue().getAndDelete(hostKey(hostId));
        if (code != null) {
            redis.delete(roomKey(code));
        }
    }

    @Override
    public void sweepExpired() {
        // No-op: el TTL de Redis expira las salas solo.
    }

    private String roomKey(String code) {
        return "mm:room:" + code;
    }

    private String hostKey(String userId) {
        return "mm:room-host:" + userId;
    }
}
