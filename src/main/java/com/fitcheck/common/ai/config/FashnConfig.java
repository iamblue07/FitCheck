package com.fitcheck.common.ai.config;

import com.fitcheck.common.ai.properties.FashnProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(FashnProperties.class)
public class FashnConfig {

    private final FashnProperties properties;

    public FashnConfig(FashnProperties properties) {
        this.properties = properties;
    }

    @Bean
    public RestClient fashnRestClient() {
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.apiKey())
                .build();
    }
}