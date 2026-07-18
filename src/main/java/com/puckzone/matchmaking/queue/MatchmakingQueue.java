package com.puckzone.matchmaking.queue;

import com.puckzone.matchmaking.model.QueueEntry;

import java.util.List;
import java.util.Optional;

/**
 * La cola de jugadores esperando rival. Dos implementaciones elegidas por
 * {@code puckzone.matchmaking.store}: en memoria (desarrollo local, réplica
 * única) y en Redis (producción, compartida entre las réplicas del servicio).
 *
 * <p>El contrato clave es que {@link #remove(String)} es un
 * <em>reclamo atómico</em>: devuelve la entrada y la saca en una sola
 * operación, de modo que si dos hilos (o dos réplicas) intentan llevarse al
 * mismo jugador, solo uno lo consigue.
 *
 * <p>El orden FIFO no lo guarda la estructura, se deriva del campo
 * {@code enqueuedAt}: {@link #snapshot()} devuelve la lista ya ordenada por
 * tiempo de llegada, que es lo que consume el algoritmo de emparejamiento.
 */
public interface MatchmakingQueue {

    /** Encola al jugador; false si ya estaba (no lo duplica ni lo pisa). */
    boolean add(QueueEntry entry);

    /** Reclamo atómico: devuelve y elimina la entrada, o vacío si no estaba. */
    Optional<QueueEntry> remove(String userId);

    boolean contains(String userId);

    Optional<QueueEntry> get(String userId);

    /** Los que esperan, ordenados por llegada (FIFO por enqueuedAt). */
    List<QueueEntry> snapshot();

    /** Número de jugadores esperando en este momento. */
    int size();
}
