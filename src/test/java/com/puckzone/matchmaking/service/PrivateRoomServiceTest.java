package com.puckzone.matchmaking.service;

import com.puckzone.matchmaking.client.GameClient;
import com.puckzone.matchmaking.config.MatchmakingProperties;
import com.puckzone.matchmaking.model.Match;
import com.puckzone.matchmaking.model.OpponentType;
import com.puckzone.matchmaking.model.QueueEntry;
import com.puckzone.matchmaking.queue.MatchmakingQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Salas privadas por código: crear/renovar, unirse (una sola vez, nunca a la
 * propia), la partida resultante sale amistosa, y la expiración por TTL.
 */
class PrivateRoomServiceTest {

    private final MatchmakingProperties props =
            new MatchmakingProperties(50, 25, Duration.ofSeconds(10), Duration.ofSeconds(60),
                    Duration.ofMinutes(10));

    private MatchmakingQueue queue;
    private GameClient gameClient;
    private MatchmakingService matchmaking;
    private PrivateRoomService rooms;

    private final QueueEntry daniel = entry("id-daniel", "daniel");
    private final QueueEntry amigo = entry("id-amigo", "amigo");

    private static QueueEntry entry(String id, String name) {
        return new QueueEntry(id, name, "eci", Instant.now());
    }

    @BeforeEach
    void setUp() {
        queue = new MatchmakingQueue();
        gameClient = mock(GameClient.class);
        matchmaking = new MatchmakingService(queue, id -> 1200, props, gameClient);
        rooms = new PrivateRoomService(matchmaking, props);
    }

    @Test
    void unirseConElCodigoCreaLaPartidaAmistosaParaAmbos() {
        String code = rooms.create(daniel).code();

        Match match = rooms.join(code, amigo);

        assertTrue(match.friendly(), "la sala privada debe salir amistosa");
        assertEquals(OpponentType.HUMAN, match.opponentType());
        assertEquals("id-daniel", match.player1().userId(), "el anfitrión es player1");
        // El anfitrión la recoge por polling, como cualquier sala
        assertEquals(match.id(), matchmaking.matchFor("id-daniel").orElseThrow().id());
        verify(gameClient).notifyMatchCreated(any());
    }

    @Test
    void elCodigoSirveUnaSolaVezYNormalizaMayusculas() {
        String code = rooms.create(daniel).code();

        rooms.join(code.toLowerCase(), amigo);

        assertThrows(NoSuchElementException.class,
                () -> rooms.join(code, entry("id-otro", "otro")),
                "el código consumido no puede servirle a un tercero");
    }

    @Test
    void nadiePuedeUnirseASuPropiaSala() {
        String code = rooms.create(daniel).code();
        assertThrows(IllegalStateException.class, () -> rooms.join(code, daniel));
    }

    @Test
    void crearDeNuevoRenuevaElCodigoYMataElViejo() {
        String oldCode = rooms.create(daniel).code();
        String newCode = rooms.create(daniel).code();

        assertNotEquals(oldCode, newCode);
        assertThrows(NoSuchElementException.class, () -> rooms.join(oldCode, amigo));
        assertEquals(newCode, rooms.roomOf("id-daniel").orElseThrow().code());
    }

    @Test
    void unirseSacaAlInvitadoDeLaColaPublica() {
        matchmaking.enqueue("id-amigo", "amigo", "eci");
        String code = rooms.create(daniel).code();

        rooms.join(code, amigo);

        assertFalse(queue.contains("id-amigo"), "jugar con el amigo gana sobre la cola");
    }

    @Test
    void laSalaExpiradaNoSirveYElBarridoLaSaca() {
        var shortTtl = new MatchmakingProperties(50, 25, Duration.ofSeconds(10),
                Duration.ofSeconds(60), Duration.ofMillis(-1)); // ya nació vencida
        var expiring = new PrivateRoomService(matchmaking, shortTtl);

        String code = expiring.create(daniel).code();

        assertTrue(expiring.roomOf("id-daniel").isEmpty(), "no debe verse una sala vencida");
        expiring.sweepExpired();
        assertThrows(NoSuchElementException.class, () -> expiring.join(code, amigo));
    }
}
