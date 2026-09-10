package com.fitcheck.feed.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "feed.executor")
public record FeedExecutorProperties(
        int corePoolSize,
        int maxPoolSize,
        int queueCapacity
) {
}