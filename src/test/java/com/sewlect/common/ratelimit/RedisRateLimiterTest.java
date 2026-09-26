package com.sewlect.common.ratelimit;

import com.sewlect.support.AbstractRedisIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class RedisRateLimiterTest extends AbstractRedisIntegrationTest {

    private static final String OPERATION_KEY = "outfit-prompt-generation";
    private static final Duration HOUR = Duration.ofHours(1);

    private RedisRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new RedisRateLimiter(REDIS_TEMPLATE);
    }

    @Test
    void tryConsume_requestsUpToTheLimit_succeedAndTheNextIsRejected() {
        String subject = UUID.randomUUID().toString();

        for (int i = 0; i < 5; i++) {
            assertThat(rateLimiter.tryConsume(subject, OPERATION_KEY, 5, HOUR)).isTrue();
        }
        assertThat(rateLimiter.tryConsume(subject, OPERATION_KEY, 5, HOUR)).isFalse();
    }

    @Test
    void tryConsume_differentOperationKeys_trackedIndependently() {
        String subject = UUID.randomUUID().toString();

        assertThat(rateLimiter.tryConsume(subject, "generation", 1, HOUR)).isTrue();
        assertThat(rateLimiter.tryConsume(subject, "generation", 1, HOUR)).isFalse();

        assertThat(rateLimiter.tryConsume(subject, "refinement", 1, HOUR)).isTrue();
    }

    @Test
    void tryConsume_differentSubjects_trackedIndependently() {
        String subjectA = UUID.randomUUID().toString();
        String subjectB = UUID.randomUUID().toString();

        assertThat(rateLimiter.tryConsume(subjectA, OPERATION_KEY, 1, HOUR)).isTrue();
        assertThat(rateLimiter.tryConsume(subjectA, OPERATION_KEY, 1, HOUR)).isFalse();

        assertThat(rateLimiter.tryConsume(subjectB, OPERATION_KEY, 1, HOUR)).isTrue();
    }

    @Test
    void tryConsume_afterWindowExpires_admitsAgain() {
        String subject = UUID.randomUUID().toString();
        Duration window = Duration.ofMillis(500);

        assertThat(rateLimiter.tryConsume(subject, OPERATION_KEY, 1, window)).isTrue();
        assertThat(rateLimiter.tryConsume(subject, OPERATION_KEY, 1, window)).isFalse();

        await().atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> rateLimiter.tryConsume(subject, OPERATION_KEY, 1, window));
    }

    @Test
    void tryConsume_redisUnavailable_rejectsRatherThanAdmits() throws IOException {
        LettuceConnectionFactory unreachable = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration("localhost", unusedPort()));
        unreachable.afterPropertiesSet();
        try {
            RedisRateLimiter limiter = new RedisRateLimiter(new StringRedisTemplate(unreachable));

            assertThat(limiter.tryConsume(UUID.randomUUID().toString(), OPERATION_KEY, 1000, HOUR)).isFalse();
        } finally {
            unreachable.destroy();
        }
    }

    private static int unusedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}