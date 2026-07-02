package com.puckzone.matchmaking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Conexión hacia puckzone-game, ajustable bajo {@code puckzone.game}.
 */
@ConfigurationProperties(prefix = "puckzone.game")
public record GameProperties(
        @DefaultValue("http://localhost:8083") String baseUrl,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("3s") Duration readTimeout
) {
}
