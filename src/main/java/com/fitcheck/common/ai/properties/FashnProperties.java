package com.fitcheck.common.ai.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fashn")
public record FashnProperties(
        String baseUrl,
        String apiKey
) {
}