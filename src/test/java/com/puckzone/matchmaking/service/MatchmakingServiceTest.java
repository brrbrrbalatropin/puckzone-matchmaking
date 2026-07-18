package com.puckzone.matchmaking.service;

import com.puckzone.matchmaking.client.GameClient;
import com.puckzone.matchmaking.config.MatchmakingProperties;
import com.puckzone.matchmaking.model.Match;
import com.puckzone.matchmaking.model.OpponentType;
import com.puckzone.matchmaking.model.QueueEntry;
import com.puckzone.matchmaking.queue.InMemoryMatchmakingQueue;
import com.puckzone.matchmaking.queue.MatchmakingQueue;
import com.puckzone.matchmaking.store.InMemoryMatchStore;
import com.puckzone.matchmaking.store.LocalPairingLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El algoritmo de emparejamiento sin red ni reloj de Spring: cola real,
 * ratings controlados por el test (el RatingProvider es un mapa) y el
 * tiempo de espera simulado encolando entradas con enqueuedAt en el
 * pasado. GameClient va mockeado: aquí se prueba a quién se empareja,
 * no el HTTP hacia game.
 */
class MatchmakingServiceTest {

    /** Ventana base ±50 que crece 25/s; bot a los 10s; retención 60s. */
    private final MatchmakingProperties props =
            new MatchmakingProperties(50, 25, Duration.ofSeconds(10), Duration.ofSeconds(60),
                    Duration.ofMinutes(10), Duration.ofMinutes(15));

    private MatchmakingQueue queue;
    private GameClient gameClient;
    private Map<String, Integer> ratings;
    private MatchmakingService service;

    @BeforeEach
    void setUp() {
        queue = new InMemoryMatchmakingQueue();
        gameClient = mock(GameClient.class);
        // El allocator reparte con floorMod(hash, shardCount): el mock debe
        // declarar al menos un shard o la asignación divide por cero.
        when(gameClient.shardCount()).thenReturn(1);
        ratings = new HashMap<>();
        service = new MatchmakingService(queue, new InMemoryMatchStore(props),
                new LocalPairingLock(), id -> ratings.getOrDefault(id, 1200), props, gameClient);
    }

    /** Encola directo con la espera simulada: enqueuedAt corrido al pasado. */
    private void waiting(String userId, int rating, int secondsWaiting) {
        ratings.put(userId, rating);
        queue.add(new QueueEntry(userId, "user-" + userId, "eci",
                Instant.now().minusSeconds(secondsWaiting)));
    }

    @Test
    void laSalaNuevaSaleAsignadaAUnShardDeterministicoPorSuId() {
        when(gameClient.shardCount()).thenReturn(2);
        waiting("solo", 1200, 15);

        Match match = service.requestBotMatch("solo");

        assertEquals(Math.floorMod(match.id().hashCode(), 2), match.shard(),
                "el shard debe salir del hash del matchId (sin estado compartido)");
        verify(gameClient).notifyMatchCreated(match);
    }

    @Test
    void ratingsDentroDeLaVentanaBaseSeEmparejanDeInmediato() {
        waiting("a", 1200, 0);
        waiting("b", 1210, 0);

        service.tick();

        Match matchA = service.matchFor("a").orElseThrow();
        Match matchB = service.matchFor("b").orElseThrow();
        assertEquals(matchA.id(), matchB.id(), "no quedaron en la misma sala");
        assertEquals(OpponentType.HUMAN, matchA.opponentType());
        assertEquals(0, queue.size(), "los emparejados deben salir de la cola");
        verify(gameClient).notifyMatchCreated(any());
    }

    @Test
    void fueraDeLaVentanaInicialNadieSeEmpareja() {
        waiting("a", 1200, 0);
        waiting("b", 1400, 0); // diff 200 > ventana base 50

        service.tick();

        assertTrue(service.matchFor("a").isEmpty());
        assertTrue(service.matchFor("b").isEmpty());
        assertEquals(2, queue.size(), "deben seguir esperando");
    }

    @Test
    void laVentanaCreceConLaEsperaYTerminaEmparejando() {
        // 7s esperando: ventana 50 + 25*7 = 225 >= diff 200
        waiting("a", 1200, 7);
        waiting("b", 1400, 7);

        service.tick();

        assertTrue(service.matchFor("a").isPresent(), "la ventana expandida no los emparejó");
        assertEquals(service.matchFor("a").orElseThrow().id(),
                service.matchFor("b").orElseThrow().id());
    }

    @Test
    void gananLosDeRatingMasCercanoYElEmpateLoDesempataLaAntiguedad() {
        waiting("a", 1200, 10);
        waiting("b", 1210, 5);
        waiting("c", 1205, 1);

        service.tick();

        // a-c y b-c empatan a diff 5 (a-b queda a 10); gana a-c porque a
        // lleva más tiempo esperando. b queda en la cola.
        assertEquals(service.matchFor("a").orElseThrow().id(),
                service.matchFor("c").orElseThrow().id(), "debió emparejar a con c");
        assertTrue(service.matchFor("b").isEmpty(), "b no debía tener sala");
        assertTrue(queue.contains("b"), "b debía seguir esperando");
    }

    @Test
    void aceptarElBotSacaDeLaColaYCreaSalaVsBot() {
        waiting("a", 1200, 11);

        Match match = service.requestBotMatch("a");

        assertEquals(OpponentType.BOT, match.opponentType());
        assertNull(match.player2(), "vs bot player2 va null");
        assertFalse(queue.contains("a"));
        assertTrue(service.matchFor("a").isPresent());
        verify(gameClient).notifyMatchCreated(any());
    }

    @Test
    void aceptarElBotConSalaHumanaYaAsignadaDevuelveEsaSala() {
        waiting("a", 1200, 0);
        waiting("b", 1210, 0);
        service.tick(); // los empareja justo antes del clic tardío

        Match match = service.requestBotMatch("a");

        assertEquals(OpponentType.HUMAN, match.opponentType(),
                "el clic tardío en el bot no debe pisar la sala humana");
    }

    @Test
    void pedirBotSinEstarEnColaNiSalaEsConflicto() {
        assertThrows(IllegalStateException.class, () -> service.requestBotMatch("fantasma"));
    }

    @Test
    void encolarseDosVecesEsConflicto() {
        service.enqueue("a", "daniel", "eci");
        assertThrows(IllegalStateException.class, () -> service.enqueue("a", "daniel", "eci"));
    }

    @Test
    void reencolarseDescartaLaSalaPendienteAnterior() {
        waiting("a", 1200, 0);
        waiting("b", 1210, 0);
        service.tick();
        assertTrue(service.matchFor("a").isPresent());

        service.enqueue("a", "daniel", "eci");

        assertTrue(service.matchFor("a").isEmpty(), "encolarse de nuevo pide partida nueva");
        assertTrue(queue.contains("a"));
    }

    @Test
    void lasSalasQueNadieRecogeExpiranTrasLaRetencion() throws InterruptedException {
        var shortRetention = new MatchmakingProperties(50, 25,
                Duration.ofSeconds(10), Duration.ofMillis(50), Duration.ofMinutes(10),
                Duration.ofMinutes(15));
        var expiringService = new MatchmakingService(queue, new InMemoryMatchStore(shortRetention),
                new LocalPairingLock(), id -> ratings.getOrDefault(id, 1200),
                shortRetention, gameClient);
        waiting("a", 1200, 11);
        expiringService.requestBotMatch("a");
        assertTrue(expiringService.matchFor("a").isPresent());

        Thread.sleep(120); // supera la retención de 50ms (ahora es el TTL del store)

        assertTrue(expiringService.matchFor("a").isEmpty(), "la sala no recogida no expiró");
    }
}
