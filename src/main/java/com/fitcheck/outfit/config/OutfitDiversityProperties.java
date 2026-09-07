package com.fitcheck.outfit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "outfit.diversity")
public record OutfitDiversityProperties(
        int maxProductRepetitions
) {
}