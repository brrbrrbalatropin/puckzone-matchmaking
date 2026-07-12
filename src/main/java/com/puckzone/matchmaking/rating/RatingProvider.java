package com.puckzone.matchmaking.rating;

/**
 * Fuente del rating (nivel) de un jugador, usado por el matchmaking para
 * emparejar rivales de rango similar.
 *
 * <p>Es una abstracción a propósito: el rating "de verdad" vive en
 * puckzone-ranking y lo consulta {@link RankingRatingProvider} por HTTP
 * (con cache y fallback al ELO inicial). La lógica de emparejamiento
 * depende solo de esta interfaz — los tests le pasan un mapa.
 */
public interface RatingProvider {

    /**
     * Devuelve el rating actual del jugador indicado.
     *
     * @param userId identificador del jugador (subject del JWT)
     * @return rating del jugador; nunca negativo
     */
    int ratingFor(String userId);
}