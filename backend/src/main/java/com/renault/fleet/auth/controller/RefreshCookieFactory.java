package com.renault.fleet.auth.controller;

import com.renault.fleet.security.JwtProperties;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * The refresh-token cookie: {@code HttpOnly; Secure; SameSite=Strict; Path=/api/auth}. Browsers accept Secure
 * cookies on http://localhost, so the same attributes work in development.
 */
@Component
public class RefreshCookieFactory {

    public static final String COOKIE_NAME = "fleet_rt";
    static final String COOKIE_PATH = "/api/auth";

    private final Duration maxAge;

    public RefreshCookieFactory(JwtProperties properties) {
        this.maxAge = properties.refreshTokenTtl();
    }

    public ResponseCookie create(String rawToken) {
        return base(rawToken).maxAge(maxAge).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(0).build();
    }

    private static ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(COOKIE_PATH);
    }
}
