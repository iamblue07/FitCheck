package com.fitcheck.feed.config;

import com.fitcheck.common.logging.MdcTaskDecorator;
import lombok.AllArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@AllArgsConstructor
@EnableConfigurationProperties(FeedExecutorProperties.class)
public class FeedAsyncConfig {

    private final FeedExecutorProperties properties;

    @Bean
    public AsyncTaskExecutor feedRefillExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.corePoolSize());
        executor.setMaxPoolSize(properties.maxPoolSize());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setThreadNamePrefix("feed-refill-");
        executor.setTaskDecorator(new MdcTaskDecorator());
        return executor;
    }
}