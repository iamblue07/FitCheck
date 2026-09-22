package com.sewlect.common.ai.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "ollama.cloud")
public record OllamaCloudProperties(
        @NotBlank String baseUrl,
        @NotBlank String apiKey,
        @NotBlank String chatModel,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @PositiveOrZero int maxRetries,
        @NotNull Duration retryDelay
) {
}