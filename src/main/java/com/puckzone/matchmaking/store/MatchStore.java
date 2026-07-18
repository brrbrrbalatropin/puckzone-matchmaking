package com.puckzone.matchmaking.store;

import com.puckzone.matchmaking.model.Match;

import java.util.Optional;

/**
 * Salas ya emparejadas, pendientes de que cada jugador las recoja por
 * polling (indexadas por userId, una entrada por jugador de la sala).
 * Viven la retención configurada ({@code match-retention}) y expiran solas.
 * En Redis todas las réplicas entregan la misma sala sin importar cuál
 * atendió el emparejamiento.
 */
public interface MatchStore {

    /** Publica la sala para un jugador (con la retención como vida útil). */
    void put(String userId, Match match);

    Optional<Match> find(String userId);

    /**
     * Descarta la sala pendiente del jugador (re-encolarse expresa querer
     * partida nueva).
     *
     * @return true si había algo que descartar
     */
    boolean discard(String userId);
}
