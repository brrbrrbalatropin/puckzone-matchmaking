package com.puckzone.matchmaking.service;

import com.puckzone.matchmaking.client.GameClient;
import com.puckzone.matchmaking.config.MatchmakingProperties;
import com.puckzone.matchmaking.model.Match;
import com.puckzone.matchmaking.model.OpponentType;
import com.puckzone.matchmaking.model.QueueEntry;
import com.puckzone.matchmaking.queue.MatchmakingQueue;
import com.puckzone.matchmaking.rating.RatingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MatchmakingService {

    private static final Logger log = LoggerFactory.getLogger(MatchmakingService.class);

    private final MatchmakingQueue queue;
    private final RatingProvider ratingProvider;
    private final MatchmakingProperties properties;
    private final GameClient gameClient;

    /** Salas ya creadas, pendientes de que el jugador las recoja por polling. */
    private final Map<String, Match> matchesByUser = new ConcurrentHashMap<>();

    public MatchmakingService(MatchmakingQueue queue,
                              RatingProvider ratingProvider,
                              MatchmakingProperties properties,
                              GameClient gameClient) {
        this.queue = queue;
        this.ratingProvider = ratingProvider;
        this.properties = properties;
        this.gameClient = gameClient;
    }

    /**
     * Mete al jugador en la cola de espera. Si tenía una sala pendiente sin
     * recoger, se descarta: encolarse de nuevo expresa querer partida nueva.
     *
     * @return la entrada creada
     * @throws IllegalStateException si ya está en la cola
     */
    public QueueEntry enqueue(String userId, String username, String university) {
        if (matchesByUser.remove(userId) != null) {
            log.info("Jugador {} se re-encoló; se descarta su sala anterior", userId);
        }
        QueueEntry entry = new QueueEntry(userId, username, university, Instant.now());
        if (!queue.add(entry)) {
            throw new IllegalStateException("El jugador ya está en la cola");
        }
        log.info("Jugador {} ({}) entró a la cola; esperando: {}", userId, username, queue.size());
        return entry;
    }

    /**
     * Saca al jugador de la cola (cancelación voluntaria).
     *
     * @return la entrada eliminada, o vacío si no estaba en cola
     */
    public Optional<QueueEntry> dequeue(String userId) {
        Optional<QueueEntry> removed = queue.remove(userId);
        removed.ifPresent(e -> log.info("Jugador {} salió de la cola", userId));
        return removed;
    }

    /** La entrada del jugador si sigue esperando rival (para calcular su espera). */
    public Optional<QueueEntry> waitingEntry(String userId) {
        return queue.get(userId);
    }

    /**
     * Sala asignada al jugador, si el matchmaking ya lo emparejó. Es lo que
     * consulta el polling del cliente cada 1-2 segundos.
     */
    public Optional<Match> matchFor(String userId) {
        return Optional.ofNullable(matchesByUser.get(userId));
    }

    /**
     * Un pase del algoritmo de emparejamiento. Corre cada segundo.
     * Desde 2026-07-08 el bot NO se asigna automáticamente: al cumplirse el
     * botTimeout el status ofrece la opción (botAvailable) y el jugador
     * decide con POST /queue/bot, o sigue esperando rival humano.
     */
    @Scheduled(fixedRateString = "${puckzone.matchmaking.tick:1s}")
    public void tick() {
        Instant now = Instant.now();
        pairHumans(now);
        evictOldMatches(now);
    }

    /**
     * El jugador aceptó jugar contra el bot: lo saca de la cola y crea la
     * sala vs bot. Si un humano lo emparejó justo antes de aceptar, se
     * devuelve esa sala (el clic tardío no rompe el match real).
     *
     * @throws IllegalStateException si no está en cola ni tiene sala
     */
    public Match requestBotMatch(String userId) {
        Match existing = matchesByUser.get(userId);
        if (existing != null) {
            log.info("Jugador {} pidió bot pero ya tenía sala {}", userId, existing.id());
            return existing;
        }
        QueueEntry entry = queue.remove(userId)
                .orElseThrow(() -> new IllegalStateException("El jugador no está en la cola"));
        createMatch(entry, null);
        return matchesByUser.get(userId);
    }

    /** Fase 1: empareja humanos compatibles, los de rating más cercano primero. */
    private void pairHumans(Instant now) {
        List<QueueEntry> waiting = queue.snapshot();
        if (waiting.size() < 2) {
            return;
        }

        Map<String, Integer> ratings = new ConcurrentHashMap<>();
        waiting.forEach(e -> ratings.put(e.userId(), ratingProvider.ratingFor(e.userId())));

        // Todos los pares compatibles, ordenados por cercanía de rating y,
        // a igualdad, por antigüedad del jugador que más lleva esperando.
        record Candidate(QueueEntry a, QueueEntry b, int ratingDiff, Instant oldest) {
        }
        List<Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < waiting.size(); i++) {
            for (int j = i + 1; j < waiting.size(); j++) {
                QueueEntry a = waiting.get(i);
                QueueEntry b = waiting.get(j);
                int diff = Math.abs(ratings.get(a.userId()) - ratings.get(b.userId()));
                long window = Math.min(
                        properties.windowFor(Duration.between(a.enqueuedAt(), now)),
                        properties.windowFor(Duration.between(b.enqueuedAt(), now)));
                if (diff <= window) {
                    Instant oldest = a.enqueuedAt().isBefore(b.enqueuedAt()) ? a.enqueuedAt() : b.enqueuedAt();
                    candidates.add(new Candidate(a, b, diff, oldest));
                }
            }
        }
        candidates.sort(Comparator.comparingInt(Candidate::ratingDiff)
                .thenComparing(Candidate::oldest));

        Set<String> matched = new HashSet<>();
        for (Candidate c : candidates) {
            if (matched.contains(c.a().userId()) || matched.contains(c.b().userId())) {
                continue;
            }
            Optional<QueueEntry> a = queue.remove(c.a().userId());
            Optional<QueueEntry> b = queue.remove(c.b().userId());
            if (a.isEmpty() || b.isEmpty()) {
                a.ifPresent(queue::add);
                b.ifPresent(queue::add);
                continue;
            }
            matched.add(c.a().userId());
            matched.add(c.b().userId());
            createMatch(a.get(), b.get());
        }
    }

    /** Fase 2: descarta salas que nadie recogió tras el periodo de retención. */
    private void evictOldMatches(Instant now) {
        Instant cutoff = now.minus(properties.matchRetention());
        matchesByUser.values().removeIf(m -> m.createdAt().isBefore(cutoff));
    }

    /**
     * Shard de game dueño de una partida nueva: hash del matchId módulo el
     * número de shards. Determinístico y sin estado — no necesita contador
     * compartido, así que seguirá funcionando igual cuando matchmaking
     * tenga varias réplicas.
     */
    private int shardFor(String matchId) {
        return Math.floorMod(matchId.hashCode(), gameClient.shardCount());
    }

    private void createMatch(QueueEntry player1, QueueEntry player2) {
        OpponentType type = player2 == null ? OpponentType.BOT : OpponentType.HUMAN;
        String matchId = UUID.randomUUID().toString();
        Match match = new Match(matchId, shardFor(matchId), player1, player2, type, false, Instant.now());
        gameClient.notifyMatchCreated(match);
        matchesByUser.put(player1.userId(), match);
        if (player2 != null) {
            matchesByUser.put(player2.userId(), match);
            log.info("Sala {} creada: {} vs {}", match.id(), player1.username(), player2.username());
        } else {
            log.info("Sala {} creada: {} vs BOT (timeout de espera)", match.id(), player1.username());
        }
    }

    /**
     * Sala amistosa de una sala privada: el amigo digitó el código del
     * anfitrión. Se crea con la misma maquinaria de la cola (game la recibe
     * ya con el flag friendly; ambos la recogen por el polling de siempre),
     * pero sin pasar por el algoritmo de rating. Si alguno estaba esperando
     * en la cola pública sale de ella: jugar con el amigo gana.
     */
    public Match createFriendlyMatch(QueueEntry host, QueueEntry guest) {
        queue.remove(host.userId());
        queue.remove(guest.userId());
        String matchId = UUID.randomUUID().toString();
        Match match = new Match(matchId, shardFor(matchId), host, guest,
                OpponentType.HUMAN, true, Instant.now());
        gameClient.notifyMatchCreated(match);
        matchesByUser.put(host.userId(), match);
        matchesByUser.put(guest.userId(), match);
        log.info("Sala amistosa {} creada: {} vs {}", match.id(), host.username(), guest.username());
        return match;
    }
}
