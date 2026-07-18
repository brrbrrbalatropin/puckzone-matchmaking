package com.puckzone.matchmaking.store;

import com.puckzone.matchmaking.model.PrivateRoom;

import java.util.Optional;

/**
 * Salas privadas esperando invitado, indexadas por código y por anfitrión
 * (máximo una por jugador). Viven {@code private-room-ttl} y expiran solas.
 *
 * <p>El contrato clave es {@link #consume(String)}: devuelve y elimina la
 * sala atómicamente — si dos amigos digitan el mismo código a la vez (o en
 * réplicas distintas), solo uno entra.
 */
public interface PrivateRoomStore {

    void save(PrivateRoom room);

    /** La sala de este código si sigue viva, sin consumirla. */
    Optional<PrivateRoom> byCode(String code);

    /** Consumo atómico del código: solo un invitado se lleva la sala. */
    Optional<PrivateRoom> consume(String code);

    /** El código de la sala viva del anfitrión, si tiene una. */
    Optional<String> codeOf(String hostId);

    /** Elimina la sala del anfitrión. Idempotente. */
    void cancel(String hostId);

    /** Barrido de expiradas; no-op donde el TTL lo hace solo (Redis). */
    void sweepExpired();
}
