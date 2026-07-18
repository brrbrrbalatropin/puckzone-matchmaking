package com.puckzone.matchmaking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * Conexión hacia los shards de puckzone-game, ajustable bajo
 * {@code puckzone.game}. Sharding por partida: cada shard es dueño de sus
 * salas y matchmaking (el allocator) decide en cuál vive cada partida.
 *
 * <p>{@code shardUrls} viene de GAME_SHARD_URLS separado por comas y EN
 * ORDEN: la posición i es el shard i, y debe ser la MISMA lista que el
 * gateway usa para sus rutas /ws-{i}.
 */
@ConfigurationProperties(prefix = "puckzone.game")
public record GameProperties(
        @DefaultValue("http://localhost:8083") List<String> shardUrls,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("3s") Duration readTimeout
) {
}
