package com.fitcheck.common.ai.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "deepinfra")
public record DeepInfraProperties(
        @NotBlank String baseUrl,
        @NotBlank String apiKey,
        @NotBlank String embeddingModel
) {
}