package com.puckzone.matchmaking.security;

public record AuthenticatedUser(
        Long userId,
        String username,
        String email,
        String university
) {
}
