package com.sewlect.common.ai.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "fashn")
public record FashnProperties(
        @NotBlank String baseUrl,
        @NotBlank String apiKey
) {
}