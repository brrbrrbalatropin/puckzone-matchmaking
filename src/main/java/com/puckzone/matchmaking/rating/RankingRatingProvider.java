package com.puckzone.matchmaking.rating;

import com.puckzone.matchmaking.config.RankingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * El {@link RatingProvider} de verdad: consulta el ELO a puckzone-ranking.
 * Reemplaza al stub que le daba a todos el mismo rating — con esto la
 * ventana expansiva del emparejamiento por fin discrimina por nivel, y el
 * rating viaja a game para elegir el nivel del bot.
 *
 * <p>Cache en memoria con TTL corto: el status se pollea cada 1-2s y el
 * ELO de quien espera en cola no cambia. Fallback al ELO inicial de la
 * plataforma (1200) si el jugador nunca ha jugado (404) o si ranking no
 * responde — la cola NUNCA se frena por culpa de ranking.
 */
@Component
public class RankingRatingProvider implements RatingProvider {

    private static final Logger log = LoggerFactory.getLogger(RankingRatingProvider.class);

    /** ELO con el que ranking crea a cada jugador nuevo; el fallback universal. */
    static final int INITIAL_ELO = 1200;

    private final RestClient restClient;
    private final long cacheTtlMillis;
    private final Map<String, CachedRating> cache = new ConcurrentHashMap<>();

    @Autowired
    public RankingRatingProvider(RestClient.Builder builder, RankingProperties properties) {
        this(buildClient(builder, properties), properties.cacheTtl());
    }

    /** Visible para tests: permite atar el RestClient a un servidor mock. */
    RankingRatingProvider(RestClient restClient, Duration cacheTtl) {
        this.restClient = restClient;
        this.cacheTtlMillis = cacheTtl.toMillis();
    }

    private static RestClient buildClient(RestClient.Builder builder, RankingProperties properties) {
        var settings = HttpClientSettings.defaults()
                .withTimeouts(properties.connectTimeout(), properties.readTimeout());
        return builder
                .baseUrl(properties.baseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
    }

    @Override
    public int ratingFor(String userId) {
        long now = System.currentTimeMillis();
        CachedRating cached = cache.get(userId);
        if (cached != null && now < cached.expiresAtMs()) {
            return cached.elo();
        }
        int elo = fetch(userId);
        // También se cachea el fallback: si ranking está caído, no se le
        // insiste en cada tick de la cola.
        cache.put(userId, new CachedRating(elo, now + cacheTtlMillis));
        return elo;
    }

    private int fetch(String userId) {
        try {
            PlayerResponse response = restClient.get()
                    .uri("/api/ranking/player/{id}", userId)
                    .retrieve()
                    .body(PlayerResponse.class);
            return response != null && response.elo() != null ? response.elo() : INITIAL_ELO;
        } catch (HttpClientErrorException.NotFound e) {
            // Nunca ha jugado: ranking lo creará con el ELO inicial.
            return INITIAL_ELO;
        } catch (RestClientException e) {
            log.warn("No se pudo consultar el rating de {} a ranking: {}", userId, e.getMessage());
            return INITIAL_ELO;
        }
    }

    /** Solo interesa el elo del PlayerRankingResponse de ranking. */
    record PlayerResponse(Integer elo) {
    }

    private record CachedRating(int elo, long expiresAtMs) {
    }
}
