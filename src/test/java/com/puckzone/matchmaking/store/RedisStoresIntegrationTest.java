package com.puckzone.matchmaking.store;

import com.puckzone.matchmaking.config.MatchmakingProperties;
import com.puckzone.matchmaking.model.Match;
import com.puckzone.matchmaking.model.OpponentType;
import com.puckzone.matchmaking.model.PrivateRoom;
import com.puckzone.matchmaking.model.QueueEntry;
import com.puckzone.matchmaking.queue.RedisMatchmakingQueue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Los stores compartidos contra un Redis REAL (localhost:6379): las
 * garantías que el algoritmo da por sentadas — reclamo atómico de la cola,
 * consumo único del código de sala privada, TTL de los matches y exclusión
 * mutua del lock. En CI el job de tests levanta Redis como service; en una
 * máquina sin Redis la clase entera se salta (assumption).
 */
class RedisStoresIntegrationTest {

    private static final MatchmakingProperties PROPS =
            new MatchmakingProperties(50, 25, Duration.ofSeconds(10), Duration.ofMillis(400),
                    Duration.ofMinutes(10), Duration.ofMinutes(15));

    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate redis;
    private static JsonMapper jsonMapper;

    @BeforeAll
    static void connectOrSkip() {
        factory = new LettuceConnectionFactory("localhost", 6379);
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        try {
            redis.getConnectionFactory().getConnection().ping();
        } catch (RuntimeException e) {
            Assumptions.abort("Sin Redis en localhost:6379 — test de integración omitido");
        }
        jsonMapper = JsonMapper.builder().build();
    }

    @AfterAll
    static void close() {
        if (factory != null) {
            factory.destroy();
        }
    }

    @BeforeEach
    void cleanKeys() {
        Set<String> keys = redis.keys("mm:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private QueueEntry entry(String userId) {
        return new QueueEntry(userId, "user-" + userId, "eci", Instant.now());
    }

    @Test
    void laColaReclamaAtomicamenteYNoDuplica() {
        var queue = new RedisMatchmakingQueue(redis, jsonMapper);

        assertTrue(queue.add(entry("ana")));
        assertFalse(queue.add(entry("ana")), "no debe duplicar al mismo jugador");
        assertTrue(queue.contains("ana"));
        assertEquals(1, queue.size());

        assertEquals("ana", queue.remove("ana").orElseThrow().userId());
        assertTrue(queue.remove("ana").isEmpty(), "el segundo reclamo debe perder");
        assertEquals(0, queue.size());
    }

    @Test
    void elSnapshotSaleOrdenadoPorLlegada() {
        var queue = new RedisMatchmakingQueue(redis, jsonMapper);
        queue.add(new QueueEntry("vieja", "vieja", "eci", Instant.now().minusSeconds(60)));
        queue.add(new QueueEntry("nueva", "nueva", "eci", Instant.now()));

        var snapshot = queue.snapshot();

        assertEquals("vieja", snapshot.get(0).userId(), "FIFO por enqueuedAt");
        assertEquals("nueva", snapshot.get(1).userId());
    }

    @Test
    void losMatchesExpiranPorTtl() throws InterruptedException {
        var store = new RedisMatchStore(redis, jsonMapper, PROPS);
        var match = new Match(UUID.randomUUID().toString(), 1, entry("ana"), entry("beto"),
                OpponentType.HUMAN, false, Instant.now());

        store.put("ana", match);
        assertEquals(match.id(), store.find("ana").orElseThrow().id());
        assertEquals(1, store.find("ana").orElseThrow().shard(), "el shard sobrevive el JSON");

        // > match-retention de 400ms
        await().atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertTrue(store.find("ana").isEmpty(),
                        "el TTL debe expirar la sala"));
    }

    @Test
    void elCodigoDeSalaPrivadaSeConsumeUnaSolaVez() {
        var store = new RedisPrivateRoomStore(redis, jsonMapper, PROPS);
        store.save(new PrivateRoom("ABC234", entry("ana"), Instant.now()));

        assertEquals("ABC234", store.codeOf("ana").orElseThrow());
        assertTrue(store.consume("ABC234").isPresent());
        assertTrue(store.consume("ABC234").isEmpty(), "el segundo invitado no entra");
        assertTrue(store.codeOf("ana").isEmpty(), "el índice del anfitrión se limpió");
    }

    @Test
    void elLockDejaEmparejarAUnaSolaReplicaALaVez() {
        var replicaA = new RedisPairingLock(redis);
        var replicaB = new RedisPairingLock(redis);

        boolean ranA = replicaA.runExclusive(() ->
                assertFalse(replicaB.runExclusive(() -> { }),
                        "mientras A empareja, B no debe poder"));

        assertTrue(ranA);
        assertTrue(replicaB.runExclusive(() -> { }), "liberado el lock, B puede");
    }
}
