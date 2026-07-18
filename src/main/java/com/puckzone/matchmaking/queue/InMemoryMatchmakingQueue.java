package com.puckzone.matchmaking.queue;

import com.puckzone.matchmaking.model.QueueEntry;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cola en memoria para desarrollo local y tests (réplica única). El
 * {@link ConcurrentHashMap} indexado por {@code userId} da las tres
 * garantías del contrato: no duplicar jugadores, reclamo atómico en
 * {@code remove} y consultas O(1).
 */
public class InMemoryMatchmakingQueue implements MatchmakingQueue {

    private final Map<String, QueueEntry> waiting = new ConcurrentHashMap<>();

    @Override
    public boolean add(QueueEntry entry) {
        return waiting.putIfAbsent(entry.userId(), entry) == null;
    }

    @Override
    public Optional<QueueEntry> remove(String userId) {
        return Optional.ofNullable(waiting.remove(userId));
    }

    @Override
    public boolean contains(String userId) {
        return waiting.containsKey(userId);
    }

    @Override
    public Optional<QueueEntry> get(String userId) {
        return Optional.ofNullable(waiting.get(userId));
    }

    @Override
    public List<QueueEntry> snapshot() {
        return waiting.values().stream()
                .sorted(Comparator.comparing(QueueEntry::enqueuedAt))
                .toList();
    }

    @Override
    public int size() {
        return waiting.size();
    }
}
