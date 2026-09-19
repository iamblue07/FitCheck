package com.fitcheck.tryon.properties;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "tryon")
public record TryonProperties(
        @Positive int rateLimitPerHour,
        @Positive int maxRetriesPerItem,
        @Positive long retryBackoffMs,
        @Positive long pollIntervalMs,
        @Positive long pollTimeoutMs,
        String fashnV16ModelName,
        String fashnV16Mode,
        String fashnMaxModelName,
        String fashnMaxResolution,
        String fashnMaxGenerationMode,
        String fashnOutputFormat
) {
}