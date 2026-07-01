package com.puckzone.matchmaking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "puckzone.matchmaking")
public record MatchmakingProperties(
        @DefaultValue("50") int windowBase,
        @DefaultValue("25") int windowGrowthPerSecond,
        @DefaultValue("10s") Duration botTimeout,
        @DefaultValue("60s") Duration matchRetention
) {

    /**
     * Semiancho de la ventana de un jugador según su tiempo de espera:
     * {@code base + crecimiento * segundosEsperando}.
     */
    public long windowFor(Duration waited) {
        return windowBase + (long) windowGrowthPerSecond * waited.toSeconds();
    }
}
