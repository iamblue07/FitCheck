package com.fitcheck.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "deepinfra")
public record DeepInfraProperties(
        String baseUrl,
        String apiKey,
        String embeddingModel
) {
}