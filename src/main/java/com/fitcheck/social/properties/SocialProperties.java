package com.fitcheck.social.properties;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "social")
public record SocialProperties(
        @Positive int defaultPageSize,
        @Positive int maxPageSize
) {
}