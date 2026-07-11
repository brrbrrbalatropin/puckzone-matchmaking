package com.puckzone.matchmaking.web;

import com.puckzone.matchmaking.model.Match;
import com.puckzone.matchmaking.model.QueueEntry;
import com.puckzone.matchmaking.security.AuthenticatedUser;
import com.puckzone.matchmaking.service.MatchmakingService;
import com.puckzone.matchmaking.service.PrivateRoomService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Salas privadas con código, para jugar amistosas (sin ELO) con un amigo.
 * Path público vía gateway: {@code /api/matching/private...} (el rewrite
 * reemplaza el prefijo por /queue). La identidad siempre sale del JWT.
 */
@RestController
@RequestMapping("/queue/private")
public class PrivateRoomController {

    private final PrivateRoomService rooms;
    private final MatchmakingService matchmaking;

    public PrivateRoomController(PrivateRoomService rooms, MatchmakingService matchmaking) {
        this.rooms = rooms;
        this.matchmaking = matchmaking;
    }

    /** Crea (o renueva) la sala del anfitrión: 201 con el código a compartir. */
    @PostMapping
    public ResponseEntity<PrivateRoomStatusResponse> create(AuthenticatedUser user) {
        var room = rooms.create(entryOf(user));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PrivateRoomStatusResponse.waiting(room.code()));
    }

    /**
     * Estado para el polling del anfitrión: WAITING mientras el código siga
     * vivo, MATCHED cuando el amigo ya entró (con la sala lista para navegar)
     * y NONE si no tiene sala (nunca creó, canceló o expiró).
     */
    @GetMapping("/status")
    public PrivateRoomStatusResponse status(AuthenticatedUser user) {
        return matchmaking.matchFor(user.userId())
                .filter(Match::friendly)
                .map(match -> PrivateRoomStatusResponse.matched(match, user.userId()))
                .orElseGet(() -> rooms.roomOf(user.userId())
                        .map(room -> PrivateRoomStatusResponse.waiting(room.code()))
                        .orElseGet(PrivateRoomStatusResponse::none));
    }

    /** Cancela la sala propia. Idempotente: 204 aunque no tuviera. */
    @DeleteMapping
    public ResponseEntity<Void> cancel(AuthenticatedUser user) {
        rooms.cancel(user.userId());
        return ResponseEntity.noContent().build();
    }

    /** El amigo digita el código: 201 con la sala ya creada en game. */
    @PostMapping("/{code}/join")
    public ResponseEntity<QueueStatusResponse> join(@PathVariable String code, AuthenticatedUser user) {
        Match match = rooms.join(code, entryOf(user));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(QueueStatusResponse.matched(match, user.userId()));
    }

    /** Código inexistente, vencido o ya usado. */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> onUnknownCode(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
    }

    /** Unirse a la propia sala. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> onConflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", e.getMessage()));
    }

    private QueueEntry entryOf(AuthenticatedUser user) {
        return new QueueEntry(user.userId(), user.username(), user.university(), Instant.now());
    }
}
