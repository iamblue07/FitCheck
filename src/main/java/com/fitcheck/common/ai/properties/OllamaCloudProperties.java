package com.fitcheck.common.ai.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ollama.cloud")
public record OllamaCloudProperties(
        String baseUrl,
        String apiKey,
        String chatModel
) {
}