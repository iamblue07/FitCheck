package com.sewlect.common.properties;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "common.http-client")
public record HttpClientProperties(
        @NotNull Duration connectTimeout
) {
}