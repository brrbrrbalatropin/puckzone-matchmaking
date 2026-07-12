package com.puckzone.matchmaking.rating;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * El proveedor de rating real: lee el elo de ranking, cachea (una consulta
 * sirve para todo el polling de la cola) y cae al ELO inicial 1200 cuando
 * el jugador no existe (404) o ranking no responde — la cola nunca se
 * frena por culpa de ranking.
 */
class RankingRatingProviderTest {

    private MockRestServiceServer server;
    private RankingRatingProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ranking-test");
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new RankingRatingProvider(builder.build(), Duration.ofMinutes(1));
    }

    @Test
    void devuelveElEloQueReportaRanking() {
        server.expect(once(), requestTo("http://ranking-test/api/ranking/player/u1"))
                .andRespond(withSuccess("{\"elo\": 1470, \"wins\": 3}", MediaType.APPLICATION_JSON));

        assertEquals(1470, provider.ratingFor("u1"));
        server.verify();
    }

    @Test
    void cacheaElRating_UnaConsultaSirveParaTodoElPolling() {
        server.expect(once(), requestTo("http://ranking-test/api/ranking/player/u1"))
                .andRespond(withSuccess("{\"elo\": 1330}", MediaType.APPLICATION_JSON));

        assertEquals(1330, provider.ratingFor("u1"));
        assertEquals(1330, provider.ratingFor("u1")); // sin segundo HTTP
        server.verify();
    }

    @Test
    void elJugadorQueNuncaHaJugadoValeElEloInicial() {
        server.expect(once(), requestTo("http://ranking-test/api/ranking/player/nuevo"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertEquals(1200, provider.ratingFor("nuevo"));
    }

    @Test
    void conRankingCaidoLaColaSigueConElEloInicial() {
        server.expect(once(), requestTo("http://ranking-test/api/ranking/player/u2"))
                .andRespond(withServerError());

        assertEquals(1200, provider.ratingFor("u2"));
        // Y el fallback también queda cacheado: no se insiste en cada tick.
        assertEquals(1200, provider.ratingFor("u2"));
        server.verify();
    }
}
