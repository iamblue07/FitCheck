package com.fitcheck.common.ai.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "deepinfra")
public record DeepInfraProperties(
        String baseUrl,
        String apiKey,
        String embeddingModel
) {
}