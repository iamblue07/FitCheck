package com.sewlect.common.cache.config;

import com.sewlect.common.cache.properties.CacheProperties;
import com.sewlect.common.cache.support.CacheSwitch;
import com.sewlect.identity.dto.StyleTagResponse;
import com.sewlect.outfit.dto.OutfitResponse;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String STYLE_TAGS_CACHE = "styleTags";

    private static final String CACHE_KEY_PREFIX = "sewlect:cache:v1:";

    @Bean
    public CacheManager cacheManager(CacheSwitch cacheSwitch,
                                     RedisConnectionFactory redisConnectionFactory,
                                     JsonMapper jsonMapper,
                                     CacheProperties cacheProperties) {
        if (!cacheSwitch.isActive()) {
            return new NoOpCacheManager();
        }

        JavaType styleTagsType = jsonMapper.getTypeFactory()
                .constructCollectionType(List.class, StyleTagResponse.class);
        RedisCacheConfiguration styleTagsConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith(CACHE_KEY_PREFIX)
                .entryTtl(cacheProperties.styleTagsTtl())
                .disableCachingNullValues()
                .serializeValuesWith(SerializationPair.fromSerializer(
                        new JacksonJsonRedisSerializer<>(jsonMapper, styleTagsType)));

        return RedisCacheManager.builder(redisConnectionFactory)
                .withCacheConfiguration(STYLE_TAGS_CACHE, styleTagsConfiguration)
                .disableCreateOnMissingCache()
                .build();
    }

    @Bean
    public RedisTemplate<String, OutfitResponse> outfitViewRedisTemplate(RedisConnectionFactory redisConnectionFactory,
                                                                         JsonMapper jsonMapper) {
        RedisTemplate<String, OutfitResponse> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);
        template.setKeySerializer(RedisSerializer.string());
        template.setValueSerializer(new JacksonJsonRedisSerializer<>(jsonMapper, OutfitResponse.class));
        return template;
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler(false);
    }
}