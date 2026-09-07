package com.fitcheck.common.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class InMemoryRateLimiter {

    private final Map<RateLimitKey, WindowState> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public boolean tryConsume(UUID userId, String operationKey, int limit, Duration window) {
        RateLimitKey key = new RateLimitKey(userId, operationKey);
        Instant now = clock.instant();
        AtomicBoolean consumed = new AtomicBoolean(false);

        windows.compute(key, (k, existing) -> {
            WindowState current = (existing == null || existing.isExpired(now))
                    ? new WindowState(now, window, 0)
                    : existing;

            if (current.count() >= limit) {
                return current;
            }

            consumed.set(true);
            return current.increment();
        });

        return consumed.get();
    }

    @Scheduled(fixedRateString = "${common.ratelimit.sweep-interval-ms}")
    public void evictExpiredWindows() {
        Instant now = clock.instant();
        int sizeBefore = windows.size();
        windows.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
        int evicted = sizeBefore - windows.size();
        if (evicted > 0) {
            log.debug("Rate limiter sweep evicted {} expired window(s), {} remaining", evicted, windows.size());
        }
    }

    private record RateLimitKey(UUID userId, String operationKey) {
    }

    private record WindowState(Instant windowStart, Duration window, int count) {

        boolean isExpired(Instant now) {
            return now.isAfter(windowStart.plus(window));
        }

        WindowState increment() {
            return new WindowState(windowStart, window, count + 1);
        }
    }
}