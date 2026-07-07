package com.puckzone.matchmaking.rating;

import org.springframework.stereotype.Component;

/**
 * Implementación temporal de {@link RatingProvider} que asigna a todos los
 * jugadores el mismo rating por defecto.
 *
 * <p>Sirve para tener el matchmaking funcional mientras puckzone-ranking (que
 * guarda el rating real) todavía no existe. Con todos en el mismo rating, la
 * ventana de emparejamiento nunca los excluye entre sí, así que el servicio se
 * comporta de facto como una cola por orden de llegada hasta que se conecte el
 * ranking real.
 *
 * <p>Cuando exista ranking, se añadirá una implementación que lo consulte por
 * HTTP y se marcará como primaria; esta clase puede quedar como fallback.
 */
@Component
public class StubRatingProvider implements RatingProvider {

    /** Rating asignado a todo jugador mientras no haya ranking real. */
    public static final int DEFAULT_RATING = 1000;

    @Override
    public int ratingFor(String userId) {
        return DEFAULT_RATING;
    }
}
