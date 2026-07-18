package com.puckzone.matchmaking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "puckzone.matchmaking")
public record MatchmakingProperties(
        @DefaultValue("50") int windowBase,
        @DefaultValue("25") int windowGrowthPerSecond,
        @DefaultValue("10s") Duration botTimeout,
        @DefaultValue("60s") Duration matchRetention,
        /** Vida de una sala privada esperando a que el amigo digite el código. */
        @DefaultValue("10m") Duration privateRoomTtl,
        /**
         * Entradas de cola más viejas que esto se barren (cliente muerto sin
         * cancelar): la cola en Redis sobrevive reinicios y no se limpia sola.
         */
        @DefaultValue("15m") Duration queueEntryTtl
) {

    /**
     * Semiancho de la ventana de un jugador según su tiempo de espera:
     * {@code base + crecimiento * segundosEsperando}.
     */
    public long windowFor(Duration waited) {
        return windowBase + (long) windowGrowthPerSecond * waited.toSeconds();
    }
}
