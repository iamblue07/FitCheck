package com.fitcheck.outfit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "outfit.compatibility")
public record OutfitCompatibilityProperties (
        BigDecimal structuredWeight,
        BigDecimal embeddingWeight
){
}
