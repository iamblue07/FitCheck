package com.sewlect.outfit.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "outfit.swap")
public record OutfitSwapProperties(
        int alternativeLimit
) {
}