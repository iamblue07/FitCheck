package com.sewlect.common.cache.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "common.cache")
public record CacheProperties(
        boolean enabled
) {
}