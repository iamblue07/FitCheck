package com.fitcheck.tryon.properties;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "tryon")
public record TryonProperties(
        @Positive int rateLimitPerHour,
        @PositiveOrZero int maxRetriesPerItem,
        @Positive long retryBackoffMs,
        @Positive long pollIntervalMs,
        @Positive long pollTimeoutMs,
        @Positive long jobTimeoutMs,
        @Positive long staleInFlightThresholdMs,
        @Positive long staleSweepIntervalMs
) {
}