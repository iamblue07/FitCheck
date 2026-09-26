package com.sewlect.identity.config;

import com.sewlect.common.cache.support.RedisCacheDefinition;
import com.sewlect.identity.dto.StyleTagResponse;
import com.sewlect.identity.properties.StyleTagCacheProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@Configuration
@EnableConfigurationProperties(StyleTagCacheProperties.class)
public class StyleTagCacheConfig {

    public static final String STYLE_TAGS_CACHE = "styleTags";

    @Bean
    public RedisCacheDefinition styleTagsCacheDefinition(RedisCacheConfiguration defaultRedisCacheConfiguration,
                                                         JsonMapper jsonMapper,
                                                         StyleTagCacheProperties properties) {
        JavaType styleTagsType = jsonMapper.getTypeFactory()
                .constructCollectionType(List.class, StyleTagResponse.class);
        return new RedisCacheDefinition(STYLE_TAGS_CACHE, defaultRedisCacheConfiguration
                .entryTtl(properties.ttl())
                .serializeValuesWith(SerializationPair.fromSerializer(
                        new JacksonJsonRedisSerializer<>(jsonMapper, styleTagsType))));
    }
}