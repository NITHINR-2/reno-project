package com.renault.fleet.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code fleet.security.jwt.*}. The HS256 secret comes from {@code JWT_SECRET} and must be at least 32 bytes;
 * startup fails otherwise. The test profile generates a random secret at runtime.
 */
@ConfigurationProperties("fleet.security.jwt")
public record JwtProperties(String secret, String issuer, Duration accessTokenTtl, Duration refreshTokenTtl) {

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET (fleet.security.jwt.secret) must be set to at least 32 bytes, e.g. `openssl rand -base64 48`.");
        }
        if (issuer == null || issuer.isBlank()) issuer = "renault-fleet-portal";
        if (accessTokenTtl == null) accessTokenTtl = Duration.ofMinutes(15);
        if (refreshTokenTtl == null) refreshTokenTtl = Duration.ofDays(7);
    }

    public byte[] secretBytes() {
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    /** Never print the secret. */
    @Override
    public String toString() {
        return "JwtProperties[issuer=" + issuer + ", accessTokenTtl=" + accessTokenTtl + ", refreshTokenTtl=" + refreshTokenTtl + "]";
    }
}
