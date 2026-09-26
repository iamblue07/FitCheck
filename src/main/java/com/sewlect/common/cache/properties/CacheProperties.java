package com.sewlect.common.cache.properties;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "common.cache")
public record CacheProperties(
        boolean enabled,
        @NotNull Duration outfitViewTtl,
        @NotNull Duration styleTagsTtl
) {
}