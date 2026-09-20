package com.sewlect.common.storage.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "r2")
public record R2Properties(
        @NotBlank String endpoint,
        @NotBlank String accessKeyId,
        @NotBlank String secretAccessKey,
        @NotBlank String bucket
) {
}