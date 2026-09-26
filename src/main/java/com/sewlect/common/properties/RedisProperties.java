package com.sewlect.common.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "common.redis")
public record RedisProperties(
        boolean enabled
) {
}