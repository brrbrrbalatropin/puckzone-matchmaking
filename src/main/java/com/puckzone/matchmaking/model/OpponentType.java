package com.puckzone.matchmaking.model;

/**
 * Indica contra quién quedó emparejado un jugador al crearse la sala.
 * HUMAN: se encontró otro jugador en la cola.
 * BOT:   no apareció rival antes del timeout y se asignó el bot.
 */
public enum OpponentType {
    HUMAN,
    BOT
}
