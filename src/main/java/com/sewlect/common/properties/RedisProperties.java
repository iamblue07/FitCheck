package com.sewlect.common.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "common.redis")
public record RedisProperties(
        boolean enabled
) {
}