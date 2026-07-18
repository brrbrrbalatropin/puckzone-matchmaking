package com.puckzone.matchmaking.store;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Lock distribuido simple sobre Redis: {@code SET mm:pair-lock <id> NX PX}.
 * Cada instancia intenta tomarlo en su tick; la que lo consigue empareja y
 * lo suelta al terminar. El TTL cubre el caso de que la dueña muera a mitad
 * de pase: el lock expira solo y otra réplica retoma al siguiente tick.
 *
 * <p>La liberación compara el id (script Lua): una réplica lenta que perdió
 * el lock por TTL no puede borrar el lock que ya tomó otra.
 */
public class RedisPairingLock implements PairingLock {

    private static final String KEY = "mm:pair-lock";
    /** Holgado frente a un pase normal (~ms); corto frente a una caída. */
    private static final Duration TTL = Duration.ofSeconds(10);

    private static final RedisScript<Long> RELEASE_IF_MINE = RedisScript.of("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0""", Long.class);

    /** Identidad de esta instancia frente al lock. */
    private final String instanceId = UUID.randomUUID().toString();
    private final StringRedisTemplate redis;

    public RedisPairingLock(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean runExclusive(Runnable pass) {
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(KEY, instanceId, TTL))) {
            return false;
        }
        try {
            pass.run();
        } finally {
            redis.execute(RELEASE_IF_MINE, List.of(KEY), instanceId);
        }
        return true;
    }
}
