package com.renault.fleet.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class PublicRateLimiterTest {

    private static Clock at(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    }

    @Test
    void allowsTheLimitPerClientPerMinuteThenReportsSecondsToTheNextWindow() {
        PublicRateLimiter limiter = new PublicRateLimiter(at("2026-10-11T10:00:15Z"), 3);
        assertThat(limiter.acquire("a")).isZero();
        assertThat(limiter.acquire("a")).isZero();
        assertThat(limiter.acquire("a")).isZero();
        assertThat(limiter.acquire("a")).isEqualTo(45);
        assertThat(limiter.acquire("b")).isZero();
    }

    @Test
    void aNewMinuteStartsAFreshWindow() {
        Instant[] now = {Instant.parse("2026-10-11T10:00:59.900Z")};
        Clock clock = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now[0]; }
        };
        PublicRateLimiter limiter = new PublicRateLimiter(clock, 1);
        assertThat(limiter.acquire("a")).isZero();
        assertThat(limiter.acquire("a")).isEqualTo(1);
        now[0] = Instant.parse("2026-10-11T10:01:00Z");
        assertThat(limiter.acquire("a")).isZero();
        assertThat(limiter.acquire("a")).isEqualTo(60);
    }
}
