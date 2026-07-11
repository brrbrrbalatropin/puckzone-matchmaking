package com.puckzone.matchmaking.model;

import java.time.Instant;

/**
 * Sala privada esperando invitado: el anfitrión la creó y tiene un código
 * corto para compartir por fuera (o por el chat del lobby). Cuando un amigo
 * digita el código se convierte en un {@link Match} amistoso y la sala
 * desaparece; si nadie llega, expira sola.
 */
public record PrivateRoom(
        String code,
        QueueEntry host,
        Instant createdAt
) {
}
