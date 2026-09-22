package com.sewlect.common.ai.config;

import com.sewlect.common.ai.properties.OllamaCloudProperties;
import com.sewlect.common.ai.util.TimeoutRequestFactories;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.web.client.ResourceAccessException;
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
        RestClient.Builder restClientBuilder = RestClient.builder()
                .requestFactory(TimeoutRequestFactories.create(properties.connectTimeout(), properties.readTimeout()))
                .defaultHeader("Authorization", authorizationHeader);
        return OllamaApi.builder()
                .baseUrl(properties.baseUrl())
                .restClientBuilder(restClientBuilder)
                .webClientBuilder(WebClient.builder().defaultHeader("Authorization", authorizationHeader))
                .build();
    }

    @Bean
    public OllamaChatModel ollamaCloudChatModel() {
        return OllamaChatModel.builder()
                .ollamaApi(ollamaCloudApi())
                .options(OllamaChatOptions.builder().model(properties.chatModel()).build())
                .retryTemplate(ollamaCloudRetryTemplate())
                .build();
    }

    private RetryTemplate ollamaCloudRetryTemplate() {
        RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxRetries(properties.maxRetries())
                .includes(TransientAiException.class, ResourceAccessException.class)
                .delay(properties.retryDelay())
                .build();
        return new RetryTemplate(retryPolicy);
    }
}