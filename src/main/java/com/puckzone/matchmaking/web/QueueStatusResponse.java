package com.puckzone.matchmaking.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.puckzone.matchmaking.model.Match;
import com.puckzone.matchmaking.model.OpponentType;
import com.puckzone.matchmaking.model.QueueEntry;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record QueueStatusResponse(
        String status,
        Long secondsWaiting,
        MatchView match
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MatchView(
            String matchId,
            String opponentType,
            String opponentUsername,
            String opponentUniversity
    ) {
    }

    public static QueueStatusResponse waiting(long secondsWaiting) {
        return new QueueStatusResponse("WAITING", secondsWaiting, null);
    }

    public static QueueStatusResponse notInQueue() {
        return new QueueStatusResponse("NOT_IN_QUEUE", null, null);
    }

    /** Arma la vista de la sala desde la perspectiva del jugador que consulta. */
    public static QueueStatusResponse matched(Match match, String requesterId) {
        QueueEntry opponent = requesterId.equals(match.player1().userId())
                ? match.player2()
                : match.player1();
        MatchView view = match.opponentType() == OpponentType.BOT
                ? new MatchView(match.id(), OpponentType.BOT.name(), "BOT", null)
                : new MatchView(match.id(), OpponentType.HUMAN.name(),
                        opponent.username(), opponent.university());
        return new QueueStatusResponse("MATCHED", null, view);
    }
}
