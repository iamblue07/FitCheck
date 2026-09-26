package com.sewlect.identity.properties;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "identity.style-tag-cache")
public record StyleTagCacheProperties(
        @NotNull Duration ttl
) {
}