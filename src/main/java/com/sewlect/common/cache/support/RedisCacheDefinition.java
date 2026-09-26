package com.sewlect.common.cache.support;

import org.springframework.data.redis.cache.RedisCacheConfiguration;

public record RedisCacheDefinition(
        String name,
        RedisCacheConfiguration configuration
) {
}