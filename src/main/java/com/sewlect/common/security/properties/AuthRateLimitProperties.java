package com.sewlect.common.security.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "auth.rate-limit")
public record AuthRateLimitProperties(
        @Positive int perIpLimit,
        @Positive int perEmailLimit,
        @NotNull Duration window,
        @NotBlank @Size(min = 32) String subjectHashSecret
) {
}