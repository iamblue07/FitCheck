package com.fitcheck.common.ai.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ollama.cloud")
public record OllamaCloudProperties(
        @NotBlank String baseUrl,
        @NotBlank String apiKey,
        @NotBlank String chatModel
) {
}