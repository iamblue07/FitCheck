package com.fitcheck.feed.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "feed")
public record FeedProperties(
        int pageSize,
        int refillThreshold
) {
}