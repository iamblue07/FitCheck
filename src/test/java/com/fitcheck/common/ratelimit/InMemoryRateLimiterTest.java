package com.fitcheck.common.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final InMemoryRateLimiter rateLimiter = new InMemoryRateLimiter(clock);

    @Test
    void tryConsume_upToLimitRequestsSucceed_thenTheNextOneFails() {
        UUID userId = UUID.randomUUID();
        String operationKey = "outfit-prompt-generation";

        for (int i = 0; i < 200; i++) {
            assertThat(rateLimiter.tryConsume(userId, operationKey, 200, Duration.ofHours(1))).isTrue();
        }

        assertThat(rateLimiter.tryConsume(userId, operationKey, 200, Duration.ofHours(1))).isFalse();
    }

    @Test
    void tryConsume_differentOperationKeys_trackedIndependently() {
        UUID userId = UUID.randomUUID();

        for (int i = 0; i < 200; i++) {
            assertThat(rateLimiter.tryConsume(userId, "generation", 200, Duration.ofHours(1))).isTrue();
        }
        assertThat(rateLimiter.tryConsume(userId, "generation", 200, Duration.ofHours(1))).isFalse();

        assertThat(rateLimiter.tryConsume(userId, "refinement", 200, Duration.ofHours(1))).isTrue();
    }

    @Test
    void tryConsume_differentUsers_trackedIndependently() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        String operationKey = "outfit-prompt-generation";

        for (int i = 0; i < 200; i++) {
            assertThat(rateLimiter.tryConsume(userA, operationKey, 200, Duration.ofHours(1))).isTrue();
        }
        assertThat(rateLimiter.tryConsume(userA, operationKey, 200, Duration.ofHours(1))).isFalse();

        assertThat(rateLimiter.tryConsume(userB, operationKey, 200, Duration.ofHours(1))).isTrue();
    }

    @Test
    void tryConsume_afterWindowExpires_resetsTheCount() {
        UUID userId = UUID.randomUUID();
        String operationKey = "outfit-prompt-generation";

        assertThat(rateLimiter.tryConsume(userId, operationKey, 1, Duration.ofHours(1))).isTrue();
        assertThat(rateLimiter.tryConsume(userId, operationKey, 1, Duration.ofHours(1))).isFalse();

        clock.advance(Duration.ofHours(1).plusSeconds(1));

        assertThat(rateLimiter.tryConsume(userId, operationKey, 1, Duration.ofHours(1))).isTrue();
    }

    @Test
    void evictExpiredWindows_doesNotDisruptAnActiveUsersWindow() {
        UUID staleUser = UUID.randomUUID();
        UUID activeUser = UUID.randomUUID();
        String operationKey = "outfit-prompt-generation";

        rateLimiter.tryConsume(staleUser, operationKey, 1, Duration.ofMinutes(1));
        clock.advance(Duration.ofMinutes(2));
        rateLimiter.tryConsume(activeUser, operationKey, 1, Duration.ofHours(1));

        rateLimiter.evictExpiredWindows();

        assertThat(rateLimiter.tryConsume(activeUser, operationKey, 1, Duration.ofHours(1))).isFalse();
        assertThat(rateLimiter.tryConsume(staleUser, operationKey, 1, Duration.ofMinutes(1))).isTrue();
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}