package com.puckzone.matchmaking.client;

import com.puckzone.matchmaking.config.GameProperties;
import com.puckzone.matchmaking.model.Match;
import com.puckzone.matchmaking.model.OpponentType;
import com.puckzone.matchmaking.model.QueueEntry;
import com.puckzone.matchmaking.rating.RatingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class GameClient {

    private static final Logger log = LoggerFactory.getLogger(GameClient.class);

    private final RestClient restClient;
    private final RatingProvider ratingProvider;

    public GameClient(RestClient.Builder builder, GameProperties properties,
                      RatingProvider ratingProvider) {
        var settings = HttpClientSettings.defaults()
                .withTimeouts(properties.connectTimeout(), properties.readTimeout());
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
        this.ratingProvider = ratingProvider;
    }

    /**
     * Notifica a game que cree la partida para la sala dada.
     *
     * @return {@code true} si game confirmó; {@code false} si no fue posible
     *         contactarlo (la sala se entrega igual al jugador)
     */
    public boolean notifyMatchCreated(Match match) {
        // El rating solo le sirve a game para el nivel del bot (1-9); en
        // partidas humanas no se manda y así no se consulta a ranking de más.
        Integer player1Rating = match.opponentType() == OpponentType.BOT
                ? ratingProvider.ratingFor(match.player1().userId())
                : null;
        var request = new CreateGameRequest(
                match.id(),
                PlayerPayload.from(match.player1()),
                PlayerPayload.from(match.player2()),
                match.opponentType().name(),
                match.friendly(),
                player1Rating);
        try {
            restClient.post()
                    .uri("/games")
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
            log.info("game confirmó la partida {}", match.id());
            return true;
        } catch (RestClientException e) {
            log.warn("No se pudo notificar a game la partida {}: {}", match.id(), e.getMessage());
            return false;
        }
    }

    /**
     * Cuerpo del POST /games de game; friendly marca las salas privadas y
     * player1Rating (solo vs bot) decide el nivel del bot.
     */
    record CreateGameRequest(String matchId, PlayerPayload player1, PlayerPayload player2,
                             String opponentType, boolean friendly, Integer player1Rating) {
    }

    record PlayerPayload(String userId, String username, String university) {

        static PlayerPayload from(QueueEntry entry) {
            return entry == null ? null
                    : new PlayerPayload(entry.userId(), entry.username(), entry.university());
        }
    }
}
