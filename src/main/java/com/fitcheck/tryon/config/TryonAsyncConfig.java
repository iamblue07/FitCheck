package com.fitcheck.tryon.config;

import com.fitcheck.common.logging.MdcTaskDecorator;
import com.fitcheck.tryon.properties.TryonExecutorProperties;
import lombok.AllArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@AllArgsConstructor
@EnableConfigurationProperties(TryonExecutorProperties.class)
public class TryonAsyncConfig {

    private final TryonExecutorProperties properties;

    @Bean
    public AsyncTaskExecutor tryonExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.corePoolSize());
        executor.setMaxPoolSize(properties.maxPoolSize());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setThreadNamePrefix("tryon-");
        executor.setTaskDecorator(new MdcTaskDecorator());
        return executor;
    }
}