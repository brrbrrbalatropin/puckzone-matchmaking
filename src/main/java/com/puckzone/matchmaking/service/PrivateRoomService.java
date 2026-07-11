package com.puckzone.matchmaking.service;

import com.puckzone.matchmaking.config.MatchmakingProperties;
import com.puckzone.matchmaking.model.Match;
import com.puckzone.matchmaking.model.PrivateRoom;
import com.puckzone.matchmaking.model.QueueEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Salas privadas por código: el anfitrión crea una, comparte el código de 6
 * caracteres y el amigo lo digita para arrancar una partida amistosa (sin
 * ELO) de inmediato. Todo en memoria, como la cola: matchmaking corre con
 * réplica única. La creación del match delega en
 * {@link MatchmakingService#createFriendlyMatch}, así la sala resultante se
 * entrega y expira por los mismos caminos que las de la cola.
 */
@Service
public class PrivateRoomService {

    private static final Logger log = LoggerFactory.getLogger(PrivateRoomService.class);

    /** Sin caracteres ambiguos (O/0, I/1/L): el código se dicta en voz alta. */
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_LENGTH = 6;

    private final Map<String, PrivateRoom> roomsByCode = new ConcurrentHashMap<>();
    /** Código de la sala de cada anfitrión: máximo una por jugador. */
    private final Map<String, String> codeByHost = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    private final MatchmakingService matchmaking;
    private final MatchmakingProperties properties;

    public PrivateRoomService(MatchmakingService matchmaking, MatchmakingProperties properties) {
        this.matchmaking = matchmaking;
        this.properties = properties;
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
        roomsByCode.put(code, room);
        codeByHost.put(host.userId(), code);
        log.info("Sala privada {} creada por {}", code, host.username());
        return room;
    }

    /** La sala del anfitrión si sigue esperando invitado (y no ha expirado). */
    public Optional<PrivateRoom> roomOf(String hostId) {
        return Optional.ofNullable(codeByHost.get(hostId))
                .map(roomsByCode::get)
                .filter(room -> !isExpired(room, Instant.now()));
    }

    /** Cancela la sala del anfitrión. Idempotente. */
    public void cancel(String hostId) {
        String code = codeByHost.remove(hostId);
        if (code != null && roomsByCode.remove(code) != null) {
            log.info("Sala privada {} cancelada por su anfitrión", code);
        }
    }

    /**
     * Un amigo digitó el código: la sala se consume (el remove es atómico —
     * si dos personas lo digitan a la vez solo una entra) y la partida
     * amistosa arranca ya.
     *
     * @return la sala creada en game
     * @throws java.util.NoSuchElementException código inexistente, vencido o ya usado
     * @throws IllegalStateException            el anfitrión intentó unirse a su propia sala
     */
    public Match join(String code, QueueEntry guest) {
        String normalized = code == null ? "" : code.strip().toUpperCase();
        PrivateRoom room = roomsByCode.get(normalized);
        if (room != null && room.host().userId().equals(guest.userId())) {
            throw new IllegalStateException("No puedes unirte a tu propia sala");
        }
        room = roomsByCode.remove(normalized);
        if (room != null) {
            codeByHost.remove(room.host().userId(), normalized);
        }
        if (room == null || isExpired(room, Instant.now())) {
            throw new java.util.NoSuchElementException("Código inválido, vencido o ya usado");
        }
        return matchmaking.createFriendlyMatch(room.host(), guest);
    }

    /** Barrido de salas que nadie reclamó dentro del TTL. */
    @Scheduled(fixedRateString = "${puckzone.matchmaking.private-room-sweep:30s}")
    public void sweepExpired() {
        Instant now = Instant.now();
        roomsByCode.values().removeIf(room -> {
            if (!isExpired(room, now)) {
                return false;
            }
            codeByHost.remove(room.host().userId(), room.code());
            log.info("Sala privada {} expiró sin invitado", room.code());
            return true;
        });
    }

    private boolean isExpired(PrivateRoom room, Instant now) {
        return room.createdAt().plus(properties.privateRoomTtl()).isBefore(now);
    }

    private String generateCode() {
        // Reintenta ante el improbabilísimo choque (31^6 combinaciones).
        while (true) {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]);
            }
            String code = sb.toString();
            if (!roomsByCode.containsKey(code)) {
                return code;
            }
        }
    }
}
