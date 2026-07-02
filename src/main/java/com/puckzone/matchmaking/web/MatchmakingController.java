package com.puckzone.matchmaking.web;

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

    public MatchmakingController(MatchmakingService service) {
        this.service = service;
    }

    /** Entra a la cola. 409 si ya estaba esperando. */
    @PostMapping
    public ResponseEntity<QueueStatusResponse> enqueue(AuthenticatedUser user) {
        service.enqueue(user.userId(), user.username(), user.university());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(QueueStatusResponse.waiting(0));
    }

    /** Estado actual: MATCHED, WAITING o NOT_IN_QUEUE. */
    @GetMapping("/status")
    public QueueStatusResponse status(AuthenticatedUser user) {
        return service.matchFor(user.userId())
                .map(match -> QueueStatusResponse.matched(match, user.userId()))
                .orElseGet(() -> service.waitingEntry(user.userId())
                        .map(this::waitingResponse)
                        .orElseGet(QueueStatusResponse::notInQueue));
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
        long seconds = Duration.between(entry.enqueuedAt(), Instant.now()).toSeconds();
        return QueueStatusResponse.waiting(seconds);
    }
}
