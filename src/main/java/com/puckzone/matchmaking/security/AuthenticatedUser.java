package com.puckzone.matchmaking.security;

public record AuthenticatedUser(
        String userId,
        String username,
        String email,
        String university
) {
}
