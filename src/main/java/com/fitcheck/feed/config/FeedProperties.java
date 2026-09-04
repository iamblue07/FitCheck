package com.fitcheck.feed.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "feed")
public record FeedProperties(
        int pageSize,
        int refillThreshold
) {
}