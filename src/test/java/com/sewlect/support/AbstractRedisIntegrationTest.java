package com.sewlect.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public abstract class AbstractRedisIntegrationTest {

    private static final int REDIS_PORT = 6379;

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:8.10.2-alpine")).withExposedPorts(REDIS_PORT);

    protected static final LettuceConnectionFactory CONNECTION_FACTORY;

    static {
        REDIS.start();
        CONNECTION_FACTORY = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(REDIS_PORT)));
        CONNECTION_FACTORY.afterPropertiesSet();
    }

    protected static StringRedisTemplate stringRedisTemplate() {
        return new StringRedisTemplate(CONNECTION_FACTORY);
    }

    @BeforeEach
    protected void flushRedis() {
        stringRedisTemplate().execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }
}