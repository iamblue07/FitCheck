package com.fitcheck.tryon.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tryon.executor")
public record TryonExecutorProperties(
        int corePoolSize,
        int maxPoolSize,
        int queueCapacity
) {
}