package com.sewlect.common.config;

import com.sewlect.common.properties.HttpClientProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(HttpClientProperties.class)
public class HttpClientConfig {

    private final HttpClientProperties properties;

    public HttpClientConfig(HttpClientProperties properties) {
        this.properties = properties;
    }

    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
    }
}