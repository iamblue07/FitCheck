package com.fitcheck.feed.service;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FeedRefillGuard {

    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public boolean tryClaim(UUID userId) {
        return inFlight.add(userId);
    }

    public void release(UUID userId) {
        inFlight.remove(userId);
    }
}