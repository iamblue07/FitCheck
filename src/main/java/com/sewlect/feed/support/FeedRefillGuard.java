package com.sewlect.feed.support;

import java.util.UUID;

public interface FeedRefillGuard {

    boolean tryClaim(UUID userId);

    void release(UUID userId);
}