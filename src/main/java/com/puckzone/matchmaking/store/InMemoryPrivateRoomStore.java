package com.puckzone.matchmaking.store;

import com.puckzone.matchmaking.config.MatchmakingProperties;
import com.puckzone.matchmaking.model.PrivateRoom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Salas privadas en memoria (desarrollo local y tests): los dos índices de
 * siempre (por código y por anfitrión) y el barrido periódico de expiradas
 * que en Redis hace el TTL.
 */
public class InMemoryPrivateRoomStore implements PrivateRoomStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryPrivateRoomStore.class);

    private final Map<String, PrivateRoom> roomsByCode = new ConcurrentHashMap<>();
    private final Map<String, String> codeByHost = new ConcurrentHashMap<>();
    private final MatchmakingProperties properties;

    public InMemoryPrivateRoomStore(MatchmakingProperties properties) {
        this.properties = properties;
    }

    @Override
    public void save(PrivateRoom room) {
        roomsByCode.put(room.code(), room);
        codeByHost.put(room.host().userId(), room.code());
    }

    @Override
    public Optional<PrivateRoom> byCode(String code) {
        return Optional.ofNullable(roomsByCode.get(code)).filter(r -> !isExpired(r, Instant.now()));
    }

    @Override
    public Optional<PrivateRoom> consume(String code) {
        PrivateRoom room = roomsByCode.remove(code);
        if (room == null) {
            return Optional.empty();
        }
        codeByHost.remove(room.host().userId(), code);
        return Optional.of(room).filter(r -> !isExpired(r, Instant.now()));
    }

    @Override
    public Optional<String> codeOf(String hostId) {
        return Optional.ofNullable(codeByHost.get(hostId))
                .filter(code -> byCode(code).isPresent());
    }

    @Override
    public void cancel(String hostId) {
        String code = codeByHost.remove(hostId);
        if (code != null) {
            roomsByCode.remove(code);
        }
    }

    @Override
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
}
