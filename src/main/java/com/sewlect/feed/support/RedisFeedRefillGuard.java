package com.sewlect.feed.support;

import com.sewlect.feed.properties.FeedProperties;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "common.redis.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(FeedProperties.class)
public class RedisFeedRefillGuard implements FeedRefillGuard {

    private static final String KEY_PREFIX = "sewlect:feed:refill:v1:";
    private static final String CLAIM_VALUE = "1";

    private final StringRedisTemplate redisTemplate;
    private final FeedProperties properties;

    @Override
    public boolean tryClaim(UUID userId) {
        try {
            Boolean claimed = redisTemplate.opsForValue()
                    .setIfAbsent(keyFor(userId), CLAIM_VALUE, Duration.ofMillis(properties.refillLockTtlMs()));
            return Boolean.TRUE.equals(claimed);
        } catch (RuntimeException e) {
            log.warn("Feed refill claim for user {} failed closed because Redis is unavailable: {}",
                    userId, e.getMessage());
            return false;
        }
    }

    @Override
    public void release(UUID userId) {
        try {
            redisTemplate.delete(keyFor(userId));
        } catch (RuntimeException e) {
            log.warn("Feed refill release for user {} failed; the claim will expire on its own: {}",
                    userId, e.getMessage());
        }
    }

    private String keyFor(UUID userId) {
        return KEY_PREFIX + userId;
    }
}