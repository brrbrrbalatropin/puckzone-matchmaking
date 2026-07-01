package com.puckzone.matchmaking.rating;

/**
 * Fuente del rating (nivel) de un jugador, usado por el matchmaking para
 * emparejar rivales de rango similar.
 *
 * <p>Es una abstracción a propósito: el rating "de verdad" vive en
 * puckzone-ranking, que todavía no existe. Mientras tanto se usa
 * {@link StubRatingProvider}. Cuando exista ranking, bastará con añadir una
 * implementación que lo consulte por HTTP; la lógica de emparejamiento no
 * cambia porque depende solo de esta interfaz.
 */
public interface RatingProvider {

    /**
     * Devuelve el rating actual del jugador indicado.
     *
     * @param userId identificador del jugador (subject del JWT)
     * @return rating del jugador; nunca negativo
     */
    int ratingFor(Long userId);
}