package com.fitcheck.outfit.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "outfit.prompt")
public record OutfitPromptProperties(
        int maxBlueprints,
        int topKPerSlot,
        int rateLimitPerHour,
        int generationPoolSize,
        int batchSize
) {
}