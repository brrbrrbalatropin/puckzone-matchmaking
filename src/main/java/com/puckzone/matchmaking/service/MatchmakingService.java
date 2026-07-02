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
    private final Map<Long, Match> matchesByUser = new ConcurrentHashMap<>();

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
     * Mete al jugador en la cola de espera.
     *
     * @return la entrada creada
     * @throws IllegalStateException si ya está en cola o ya tiene una sala
     *                               pendiente de recoger
     */
    public QueueEntry enqueue(Long userId, String username, String university) {
        if (matchesByUser.containsKey(userId)) {
            throw new IllegalStateException("El jugador ya tiene una sala asignada");
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
    public Optional<QueueEntry> dequeue(Long userId) {
        Optional<QueueEntry> removed = queue.remove(userId);
        removed.ifPresent(e -> log.info("Jugador {} salió de la cola", userId));
        return removed;
    }

    /** ¿El jugador sigue esperando rival? */
    public boolean isWaiting(Long userId) {
        return queue.contains(userId);
    }

    /**
     * Sala asignada al jugador, si el matchmaking ya lo emparejó. Es lo que
     * consulta el polling del cliente cada 1-2 segundos.
     */
    public Optional<Match> matchFor(Long userId) {
        return Optional.ofNullable(matchesByUser.get(userId));
    }

    /**
     * Un pase del algoritmo de emparejamiento. Corre cada segundo.
     */
    @Scheduled(fixedRateString = "${puckzone.matchmaking.tick:1s}")
    public void tick() {
        Instant now = Instant.now();
        pairHumans(now);
        assignBots(now);
        evictOldMatches(now);
    }

    /** Fase 1: empareja humanos compatibles, los de rating más cercano primero. */
    private void pairHumans(Instant now) {
        List<QueueEntry> waiting = queue.snapshot();
        if (waiting.size() < 2) {
            return;
        }

        Map<Long, Integer> ratings = new ConcurrentHashMap<>();
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

        Set<Long> matched = new HashSet<>();
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

    /** Fase 2: quien agotó el timeout sin rival humano juega contra el bot. */
    private void assignBots(Instant now) {
        for (QueueEntry entry : queue.snapshot()) {
            Duration waited = Duration.between(entry.enqueuedAt(), now);
            if (waited.compareTo(properties.botTimeout()) >= 0
                    && queue.remove(entry.userId()).isPresent()) {
                createMatch(entry, null);
            }
        }
    }

    /** Fase 3: descarta salas que nadie recogió tras el periodo de retención. */
    private void evictOldMatches(Instant now) {
        Instant cutoff = now.minus(properties.matchRetention());
        matchesByUser.values().removeIf(m -> m.createdAt().isBefore(cutoff));
    }

    private void createMatch(QueueEntry player1, QueueEntry player2) {
        OpponentType type = player2 == null ? OpponentType.BOT : OpponentType.HUMAN;
        Match match = new Match(UUID.randomUUID().toString(), player1, player2, type, Instant.now());
        gameClient.notifyMatchCreated(match);
        matchesByUser.put(player1.userId(), match);
        if (player2 != null) {
            matchesByUser.put(player2.userId(), match);
            log.info("Sala {} creada: {} vs {}", match.id(), player1.username(), player2.username());
        } else {
            log.info("Sala {} creada: {} vs BOT (timeout de espera)", match.id(), player1.username());
        }
    }
}
