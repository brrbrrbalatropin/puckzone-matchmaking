package com.puckzone.matchmaking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Conexión hacia puckzone-ranking (la fuente del ELO real), ajustable bajo
 * {@code puckzone.ranking}. El cacheTtl amortigua el polling del status:
 * el rating de un jugador no cambia mientras espera en la cola.
 */
@ConfigurationProperties(prefix = "puckzone.ranking")
public record RankingProperties(
        @DefaultValue("http://localhost:8084") String baseUrl,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("3s") Duration readTimeout,
        @DefaultValue("60s") Duration cacheTtl
) {
}
