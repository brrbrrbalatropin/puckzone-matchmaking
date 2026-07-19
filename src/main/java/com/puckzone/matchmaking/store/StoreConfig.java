package com.puckzone.matchmaking.store;

import com.puckzone.matchmaking.config.MatchmakingProperties;
import com.puckzone.matchmaking.queue.InMemoryMatchmakingQueue;
import com.puckzone.matchmaking.queue.MatchmakingQueue;
import com.puckzone.matchmaking.queue.RedisMatchmakingQueue;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Elige dónde vive el estado del matchmaking según
 * {@code puckzone.matchmaking.store}:
 *
 * <ul>
 *   <li>{@code memory} — desarrollo local y tests, réplica única (default
 *       del application.yaml, como el secreto JWT de dev).</li>
 *   <li>{@code redis} — producción (lo inyecta Terraform): cola, matches y
 *       salas privadas compartidos entre réplicas + lock del emparejador.
 *       Sin esto NO se puede subir de 1 réplica: cada instancia vería una
 *       cola distinta.</li>
 * </ul>
 */
public final class StoreConfig {

    // Solo agrupa las dos configuraciones anidadas (el scan las registra
    // directamente); no se instancia.
    private StoreConfig() {
    }

    @Configuration
    @ConditionalOnProperty(name = "puckzone.matchmaking.store", havingValue = "memory")
    static class MemoryStores {

        @Bean
        MatchmakingQueue matchmakingQueue() {
            return new InMemoryMatchmakingQueue();
        }

        @Bean
        MatchStore matchStore(MatchmakingProperties properties) {
            return new InMemoryMatchStore(properties);
        }

        @Bean
        PrivateRoomStore privateRoomStore(MatchmakingProperties properties) {
            return new InMemoryPrivateRoomStore(properties);
        }

        @Bean
        PairingLock pairingLock() {
            return new LocalPairingLock();
        }
    }

    // matchIfMissing: si la property faltara en prod, mejor exigir Redis (y
    // fallar visible si no está) que degradar en silencio a memoria con 2
    // réplicas viendo colas distintas.
    @Configuration
    @ConditionalOnProperty(name = "puckzone.matchmaking.store", havingValue = "redis",
            matchIfMissing = true)
    static class RedisStores {

        @Bean
        MatchmakingQueue matchmakingQueue(StringRedisTemplate redis, JsonMapper jsonMapper) {
            return new RedisMatchmakingQueue(redis, jsonMapper);
        }

        @Bean
        MatchStore matchStore(StringRedisTemplate redis, JsonMapper jsonMapper,
                              MatchmakingProperties properties) {
            return new RedisMatchStore(redis, jsonMapper, properties);
        }

        @Bean
        PrivateRoomStore privateRoomStore(StringRedisTemplate redis, JsonMapper jsonMapper,
                                          MatchmakingProperties properties) {
            return new RedisPrivateRoomStore(redis, jsonMapper, properties);
        }

        @Bean
        PairingLock pairingLock(StringRedisTemplate redis) {
            return new RedisPairingLock(redis);
        }
    }
}
