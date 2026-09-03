package com.fitcheck.outfit.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "outfit.generation")
public record OutfitGenerationProperties(
        int batchSize,
        int maxGenerationAttempts,
        BigDecimal budgetTolerance,
        int topKPerSlot,
        int beamWidth
) {
}