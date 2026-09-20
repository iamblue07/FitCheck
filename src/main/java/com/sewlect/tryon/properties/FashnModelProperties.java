package com.sewlect.tryon.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "tryon.fashn")
public record FashnModelProperties(
        @NotBlank String v16ModelName,
        @NotBlank String v16Mode,
        @NotBlank String maxModelName,
        @NotBlank String maxResolution,
        @NotBlank String maxGenerationMode,
        @NotBlank String outputFormat
) {
}