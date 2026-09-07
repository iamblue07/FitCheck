package com.fitcheck.common.config;

import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(OllamaCloudProperties.class)
public class OllamaCloudConfig {

    private final OllamaCloudProperties properties;

    public OllamaCloudConfig(OllamaCloudProperties properties) {
        this.properties = properties;
    }

    @Bean
    public OllamaApi ollamaCloudApi() {
        String authorizationHeader = "Bearer " + properties.apiKey();
        return OllamaApi.builder()
                .baseUrl(properties.baseUrl())
                .restClientBuilder(RestClient.builder().defaultHeader("Authorization", authorizationHeader))
                .webClientBuilder(WebClient.builder().defaultHeader("Authorization", authorizationHeader))
                .build();
    }

    @Bean
    public OllamaChatModel ollamaCloudChatModel() {
        return OllamaChatModel.builder()
                .ollamaApi(ollamaCloudApi())
                .options(OllamaChatOptions.builder().model(properties.chatModel()).build())
                .build();
    }
}