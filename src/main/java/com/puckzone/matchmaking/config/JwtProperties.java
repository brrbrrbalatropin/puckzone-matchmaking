package com.puckzone.matchmaking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "puckzone.jwt")
public record JwtProperties(
        @DefaultValue("puckzone-dev-secret-solo-para-desarrollo-local") String secret
) {
}
