package com.sewlect.common.cache.support;

import com.sewlect.common.cache.properties.CacheProperties;
import com.sewlect.common.properties.RedisProperties;
import lombok.Getter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Component
@EnableConfigurationProperties({CacheProperties.class, RedisProperties.class})
public class CacheSwitch {

    private final boolean active;

    public CacheSwitch(CacheProperties cacheProperties, RedisProperties redisProperties) {
        this.active = cacheProperties.enabled() && redisProperties.enabled();
    }
}