package com.sewlect.common.cache.config;

import com.sewlect.common.cache.support.CacheSwitch;
import com.sewlect.common.cache.support.RedisCacheDefinition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    private static final String CACHE_KEY_PREFIX = "sewlect:cache:v1:";

    @Bean
    public RedisCacheConfiguration defaultRedisCacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith(CACHE_KEY_PREFIX)
                .disableCachingNullValues();
    }

    @Bean
    public CacheManager cacheManager(CacheSwitch cacheSwitch,
                                     RedisConnectionFactory redisConnectionFactory,
                                     RedisCacheConfiguration defaultRedisCacheConfiguration,
                                     ObjectProvider<RedisCacheDefinition> cacheDefinitions) {
        if (!cacheSwitch.isActive()) {
            return new NoOpCacheManager();
        }

        RedisCacheManager.RedisCacheManagerBuilder builder = RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultRedisCacheConfiguration)
                .disableCreateOnMissingCache();
        cacheDefinitions.orderedStream()
                .forEach(definition -> builder.withCacheConfiguration(definition.name(), definition.configuration()));
        return builder.build();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler(false);
    }
}