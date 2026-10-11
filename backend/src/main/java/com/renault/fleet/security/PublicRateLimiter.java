package com.renault.fleet.security;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Fixed one-minute window per client address for the unauthenticated {@code /api/public/**} endpoints. In-memory, so
 * the limit is per instance; the client address is the socket peer (X-Forwarded-For is not trusted).
 */
@Component
public class PublicRateLimiter {

    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final Clock clock;
    private final int perMinute;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public PublicRateLimiter(Clock clock, @Value("${fleet.public-api.rate-limit.per-minute:30}") int perMinute) {
        this.clock = clock;
        this.perMinute = perMinute;
    }

    /** @return seconds to wait when the client is over the limit, or 0 when the request may proceed */
    public long acquire(String client) {
        long now = clock.millis();
        long minute = now / 60_000;
        if (windows.size() > MAX_TRACKED_CLIENTS) {
            windows.values().removeIf(w -> w.minute < minute);
        }
        Window window = windows.compute(client, (key, w) -> w == null || w.minute != minute
                ? new Window(minute, 1) : new Window(minute, w.count + 1));
        return window.count <= perMinute ? 0 : Math.max(1, ((minute + 1) * 60_000 - now + 999) / 1000);
    }

    public int perMinute() {
        return perMinute;
    }

    private record Window(long minute, int count) {
    }
}
