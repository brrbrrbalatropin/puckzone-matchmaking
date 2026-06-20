package com.puckzone.matchmaking.model;

import java.time.Instant;

/**
 * Un jugador esperando en la cola de matchmaking.
 *
 * <p>Es un valor inmutable: una vez encolado, sus datos no cambian. El campo
 * {@code enqueuedAt} cumple doble función: define el orden FIFO de la cola y
 * permite calcular cuándo vence el timeout para asignar un bot.
 *
 * <p>Los datos del jugador (userId, username, university) provienen del JWT
 * emitido por puckzone-auth.
 */
public record QueueEntry(
        Long userId,
        String username,
        String university,
        Instant enqueuedAt
) {
}
