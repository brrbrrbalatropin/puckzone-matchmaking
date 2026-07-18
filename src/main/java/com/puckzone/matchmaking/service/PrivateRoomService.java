package com.puckzone.matchmaking.service;

import com.puckzone.matchmaking.model.Match;
import com.puckzone.matchmaking.model.PrivateRoom;
import com.puckzone.matchmaking.model.QueueEntry;
import com.puckzone.matchmaking.store.PrivateRoomStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;

/**
 * Salas privadas por código: el anfitrión crea una, comparte el código de 6
 * caracteres y el amigo lo digita para arrancar una partida amistosa (sin
 * ELO) de inmediato. Las salas viven en el {@link PrivateRoomStore}
 * (memoria en local, Redis en producción: cualquier réplica atiende el
 * código sin importar cuál creó la sala). La creación del match delega en
 * {@link MatchmakingService#createFriendlyMatch}, así la sala resultante se
 * entrega y expira por los mismos caminos que las de la cola.
 */
@Service
public class PrivateRoomService {

    private static final Logger log = LoggerFactory.getLogger(PrivateRoomService.class);

    /** Sin caracteres ambiguos (O/0, I/1/L): el código se dicta en voz alta. */
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_LENGTH = 6;

    private final SecureRandom random = new SecureRandom();

    private final MatchmakingService matchmaking;
    private final PrivateRoomStore rooms;

    public PrivateRoomService(MatchmakingService matchmaking, PrivateRoomStore rooms) {
        this.matchmaking = matchmaking;
        this.rooms = rooms;
    }

    /**
     * Crea (o reemplaza) la sala privada del anfitrión y devuelve su código.
     * Crear de nuevo expresa querer un código fresco: la sala anterior se
     * descarta y su código viejo deja de servir.
     */
    public PrivateRoom create(QueueEntry host) {
        cancel(host.userId());
        String code = generateCode();
        PrivateRoom room = new PrivateRoom(code, host, Instant.now());
        rooms.save(room);
        log.info("Sala privada {} creada por {}", code, host.username());
        return room;
    }

    /** La sala del anfitrión si sigue esperando invitado (y no ha expirado). */
    public Optional<PrivateRoom> roomOf(String hostId) {
        return rooms.codeOf(hostId).flatMap(rooms::byCode);
    }

    /** Cancela la sala del anfitrión. Idempotente. */
    public void cancel(String hostId) {
        rooms.cancel(hostId);
    }

    /**
     * Un amigo digitó el código: la sala se consume (el consumo del store es
     * atómico — si dos personas lo digitan a la vez solo una entra) y la
     * partida amistosa arranca ya.
     *
     * @return la sala creada en game
     * @throws java.util.NoSuchElementException código inexistente, vencido o ya usado
     * @throws IllegalStateException            el anfitrión intentó unirse a su propia sala
     */
    public Match join(String code, QueueEntry guest) {
        String normalized = code == null ? "" : code.strip().toUpperCase();
        // Chequeo amable ANTES de consumir: el anfitrión que digita su propio
        // código no debe quemar la sala.
        if (rooms.byCode(normalized)
                .filter(room -> room.host().userId().equals(guest.userId()))
                .isPresent()) {
            throw new IllegalStateException("No puedes unirte a tu propia sala");
        }
        PrivateRoom room = rooms.consume(normalized)
                .orElseThrow(() -> new java.util.NoSuchElementException(
                        "Código inválido, vencido o ya usado"));
        return matchmaking.createFriendlyMatch(room.host(), guest);
    }

    /** Barrido de salas vencidas (no-op en Redis: su TTL las expira solo). */
    @Scheduled(fixedRateString = "${puckzone.matchmaking.private-room-sweep:30s}")
    public void sweepExpired() {
        rooms.sweepExpired();
    }

    private String generateCode() {
        // Reintenta ante el improbabilísimo choque (31^6 combinaciones).
        while (true) {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]);
            }
            String code = sb.toString();
            if (rooms.byCode(code).isEmpty()) {
                return code;
            }
        }
    }
}
