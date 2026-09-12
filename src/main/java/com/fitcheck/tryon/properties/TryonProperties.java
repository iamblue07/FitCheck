package com.fitcheck.tryon.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tryon")
public record TryonProperties(
        int rateLimitPerHour,
        int maxRetriesPerItem,
        long retryBackoffMs,
        long pollIntervalMs,
        long pollTimeoutMs,
        String fashnV16ModelName,
        String fashnV16Mode,
        String fashnMaxModelName,
        String fashnMaxResolution,
        String fashnMaxGenerationMode,
        String fashnOutputFormat
) {
}