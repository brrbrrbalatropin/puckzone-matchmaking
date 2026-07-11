package com.puckzone.matchmaking.model;

import java.time.Instant;

/**
 * Una sala ya emparejada, resultado del matchmaking.
 *
 * <p>El {@code id} es un UUID que identifica la partida y que más adelante se
 * le entrega a puckzone-game (vía HTTP) para que arranque el motor de juego.
 *
 * <p>Si el rival es un bot, {@code player2} es {@code null} y
 * {@code opponentType} es {@link OpponentType#BOT}.
 *
 * <p>{@code friendly} marca las salas privadas entre amigos (creadas por
 * código, no por la cola): se juegan igual pero no mueven ELO ni V-D.
 */
public record Match(
        String id,
        QueueEntry player1,
        QueueEntry player2,
        OpponentType opponentType,
        boolean friendly,
        Instant createdAt
) {
}
