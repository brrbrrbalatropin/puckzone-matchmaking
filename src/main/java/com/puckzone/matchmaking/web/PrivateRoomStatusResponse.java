package com.puckzone.matchmaking.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.puckzone.matchmaking.model.Match;

/**
 * Lo que ve el anfitrión de una sala privada al polear su estado:
 * WAITING con el código para compartir, MATCHED con la sala lista, o NONE.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PrivateRoomStatusResponse(
        String status,
        String code,
        QueueStatusResponse.MatchView match
) {

    public static PrivateRoomStatusResponse waiting(String code) {
        return new PrivateRoomStatusResponse("WAITING", code, null);
    }

    public static PrivateRoomStatusResponse none() {
        return new PrivateRoomStatusResponse("NONE", null, null);
    }

    public static PrivateRoomStatusResponse matched(Match match, String requesterId) {
        return new PrivateRoomStatusResponse("MATCHED", null,
                QueueStatusResponse.matched(match, requesterId).match());
    }
}
