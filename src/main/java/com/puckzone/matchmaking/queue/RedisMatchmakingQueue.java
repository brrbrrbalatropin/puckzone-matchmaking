package com.puckzone.matchmaking.queue;

import com.puckzone.matchmaking.model.QueueEntry;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import tools.jackson.databind.json.JsonMapper;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Cola compartida en Redis (hash {@code mm:queue}, campo = userId, valor =
 * QueueEntry en JSON): todas las réplicas de matchmaking ven a los mismos
 * jugadores esperando, y la cola sobrevive a un reinicio del servicio.
 *
 * <p>El reclamo atómico de {@link #remove(String)} es un script Lua
 * (HGET + HDEL en una sola operación): entre varias réplicas solo una se
 * lleva al jugador — la misma garantía que daba el ConcurrentHashMap, pero
 * a nivel de clúster.
 */
public class RedisMatchmakingQueue implements MatchmakingQueue {

    private static final String KEY = "mm:queue";

    /** HGET + HDEL atómicos: devuelve la entrada solo a quien la elimina. */
    private static final RedisScript<String> CLAIM = RedisScript.of("""
            local v = redis.call('HGET', KEYS[1], ARGV[1])
            if v then redis.call('HDEL', KEYS[1], ARGV[1]) end
            return v""", String.class);

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;

    public RedisMatchmakingQueue(StringRedisTemplate redis, JsonMapper jsonMapper) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public boolean add(QueueEntry entry) {
        return Boolean.TRUE.equals(redis.opsForHash()
                .putIfAbsent(KEY, entry.userId(), jsonMapper.writeValueAsString(entry)));
    }

    @Override
    public Optional<QueueEntry> remove(String userId) {
        return Optional.ofNullable(redis.execute(CLAIM, List.of(KEY), userId))
                .map(json -> jsonMapper.readValue(json, QueueEntry.class));
    }

    @Override
    public boolean contains(String userId) {
        return Boolean.TRUE.equals(redis.opsForHash().hasKey(KEY, userId));
    }

    @Override
    public Optional<QueueEntry> get(String userId) {
        Object json = redis.opsForHash().get(KEY, userId);
        return Optional.ofNullable((String) json)
                .map(v -> jsonMapper.readValue(v, QueueEntry.class));
    }

    @Override
    public List<QueueEntry> snapshot() {
        return redis.opsForHash().values(KEY).stream()
                .map(json -> jsonMapper.readValue((String) json, QueueEntry.class))
                .sorted(Comparator.comparing(QueueEntry::enqueuedAt))
                .toList();
    }

    @Override
    public int size() {
        Long size = redis.opsForHash().size(KEY);
        return size == null ? 0 : size.intValue();
    }
}
