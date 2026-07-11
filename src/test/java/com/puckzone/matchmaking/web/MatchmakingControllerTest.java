package com.puckzone.matchmaking.web;

import com.puckzone.matchmaking.client.GameClient;
import com.puckzone.matchmaking.config.MatchmakingProperties;
import com.puckzone.matchmaking.model.QueueEntry;
import com.puckzone.matchmaking.queue.MatchmakingQueue;
import com.puckzone.matchmaking.security.AuthenticatedUser;
import com.puckzone.matchmaking.service.MatchmakingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * El contrato del polling GET /queue/status que consume el frontend:
 * WAITING sin oferta de bot antes del timeout, botAvailable=true después,
 * MATCHED con la vista del rival desde la perspectiva de quien pregunta,
 * y NOT_IN_QUEUE. Servicio real con cola real; solo el HTTP a game es mock.
 */
class MatchmakingControllerTest {

    private static final AuthenticatedUser DANIEL =
            new AuthenticatedUser("id-daniel", "daniel", "daniel@eci.edu.co", "eci");

    /** Bot a los 10s, igual que producción. */
    private final MatchmakingProperties props =
            new MatchmakingProperties(50, 25, Duration.ofSeconds(10), Duration.ofSeconds(60));

    private MatchmakingQueue queue;
    private MatchmakingService service;
    private MatchmakingController controller;

    @BeforeEach
    void setUp() {
        queue = new MatchmakingQueue();
        service = new MatchmakingService(queue, id -> 1200, props, mock(GameClient.class));
        controller = new MatchmakingController(service, props);
    }

    private void waitingFor(AuthenticatedUser user, int secondsWaiting) {
        queue.add(new QueueEntry(user.userId(), user.username(), user.university(),
                Instant.now().minusSeconds(secondsWaiting)));
    }

    @Test
    void antesDelTimeoutEsperaSinOfertaDeBot() {
        waitingFor(DANIEL, 3);

        QueueStatusResponse status = controller.status(DANIEL);

        assertEquals("WAITING", status.status());
        assertNull(status.botAvailable(), "el bot no se ofrece antes del timeout");
        assertTrue(status.secondsWaiting() >= 3);
    }

    @Test
    void cumplidoElTimeoutElStatusOfreceElBot() {
        waitingFor(DANIEL, 11);

        QueueStatusResponse status = controller.status(DANIEL);

        assertEquals("WAITING", status.status());
        assertEquals(Boolean.TRUE, status.botAvailable(), "a los 10s debe ofrecerse el bot");
    }

    @Test
    void emparejadoElStatusTraeLaVistaDelRival() {
        var rival = new AuthenticatedUser("id-rival", "rival", "rival@unal.edu.co", "unal");
        waitingFor(DANIEL, 0);
        waitingFor(rival, 0);
        service.tick();

        QueueStatusResponse status = controller.status(DANIEL);

        assertEquals("MATCHED", status.status());
        assertEquals("rival", status.match().opponentUsername(),
                "el rival debe verse desde la perspectiva de quien pregunta");
        assertEquals("unal", status.match().opponentUniversity());
    }

    @Test
    void sinColaNiSalaElStatusEsNotInQueue() {
        assertEquals("NOT_IN_QUEUE", controller.status(DANIEL).status());
    }
}
