package com.sewlect.outfit.properties;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "outfit.view-cache")
public record OutfitViewCacheProperties(
        @NotNull Duration ttl
) {
}