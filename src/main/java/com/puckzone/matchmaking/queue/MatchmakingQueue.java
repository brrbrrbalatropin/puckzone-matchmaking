package com.puckzone.matchmaking.queue;

import com.puckzone.matchmaking.model.QueueEntry;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Almacén en memoria de los jugadores que esperan rival.
 *
 * <p>Es seguro para concurrencia: varios jugadores pueden encolarse a la vez
 * mientras el <em>tick</em> del matchmaking recorre la cola. Se apoya en un
 * {@link ConcurrentHashMap} indexado por {@code userId}, lo que da tres cosas:
 * evita que un mismo jugador se encole dos veces, y permite quitarlo o
 * consultarlo en O(1).
 *
 * <p>El orden FIFO no lo guarda la estructura, se deriva del campo
 * {@code enqueuedAt}: {@link #snapshot()} devuelve la lista ya ordenada por
 * tiempo de llegada, que es lo que consume el algoritmo de emparejamiento.
 *
 * <p>Solo contiene a quienes esperan. La sala resultante (una vez emparejado un
 * jugador y sacado de la cola) se guarda en otro sitio, responsabilidad del
 * servicio de matchmaking.
 */
@Component
public class MatchmakingQueue {

    private final Map<String, QueueEntry> waiting = new ConcurrentHashMap<>();


    public boolean add(QueueEntry entry) {
        return waiting.putIfAbsent(entry.userId(), entry) == null;
    }


    public Optional<QueueEntry> remove(String userId) {
        return Optional.ofNullable(waiting.remove(userId));
    }

    public boolean contains(String userId) {
        return waiting.containsKey(userId);
    }

    public Optional<QueueEntry> get(String userId) {
        return Optional.ofNullable(waiting.get(userId));
    }

    public List<QueueEntry> snapshot() {
        return waiting.values().stream()
                .sorted(Comparator.comparing(QueueEntry::enqueuedAt))
                .toList();
    }

    /** Número de jugadores esperando en este momento. */
    public int size() {
        return waiting.size();
    }
}
