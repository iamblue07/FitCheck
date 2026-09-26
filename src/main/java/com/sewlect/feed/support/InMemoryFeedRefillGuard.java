package com.sewlect.feed.support;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "common.redis.enabled", havingValue = "false")
public class InMemoryFeedRefillGuard implements FeedRefillGuard {

    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    @Override
    public boolean tryClaim(UUID userId) {
        return inFlight.add(userId);
    }

    @Override
    public void release(UUID userId) {
        inFlight.remove(userId);
    }
}