package com.fitcheck.outfit.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "outfit.diversity")
public record OutfitDiversityProperties(
        int maxProductRepetitions
) {
}