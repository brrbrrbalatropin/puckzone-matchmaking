package com.puckzone.matchmaking.web;

import com.puckzone.matchmaking.config.MatchmakingProperties;
import com.puckzone.matchmaking.model.Match;
import com.puckzone.matchmaking.model.QueueEntry;
import com.puckzone.matchmaking.security.AuthenticatedUser;
import com.puckzone.matchmaking.service.MatchmakingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/queue")
public class MatchmakingController {

    private final MatchmakingService service;
    private final MatchmakingProperties properties;

    public MatchmakingController(MatchmakingService service, MatchmakingProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    /** Entra a la cola. 409 si ya estaba esperando. */
    @PostMapping
    public ResponseEntity<QueueStatusResponse> enqueue(AuthenticatedUser user) {
        service.enqueue(user.userId(), user.username(), user.university());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(QueueStatusResponse.waiting(0, false));
    }

    /** Estado actual: MATCHED, WAITING (con botAvailable tras el timeout) o NOT_IN_QUEUE. */
    @GetMapping("/status")
    public QueueStatusResponse status(AuthenticatedUser user) {
        return service.matchFor(user.userId())
                .map(match -> QueueStatusResponse.matched(match, user.userId()))
                .orElseGet(() -> service.waitingEntry(user.userId())
                        .map(this::waitingResponse)
                        .orElseGet(QueueStatusResponse::notInQueue));
    }

    /**
     * El jugador aceptó la oferta de jugar contra el bot: sale de la cola y
     * recibe su sala de una vez. 409 si ya no estaba en cola ni con sala.
     */
    @PostMapping("/bot")
    public ResponseEntity<QueueStatusResponse> playBot(AuthenticatedUser user) {
        Match match = service.requestBotMatch(user.userId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(QueueStatusResponse.matched(match, user.userId()));
    }

    /** Cancela la espera. Idempotente: 204 aunque ya no estuviera en cola. */
    @DeleteMapping
    public ResponseEntity<Void> cancel(AuthenticatedUser user) {
        service.dequeue(user.userId());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> onConflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", e.getMessage()));
    }

    private QueueStatusResponse waitingResponse(QueueEntry entry) {
        Duration waited = Duration.between(entry.enqueuedAt(), Instant.now());
        boolean botAvailable = waited.compareTo(properties.botTimeout()) >= 0;
        return QueueStatusResponse.waiting(waited.toSeconds(), botAvailable);
    }
}
