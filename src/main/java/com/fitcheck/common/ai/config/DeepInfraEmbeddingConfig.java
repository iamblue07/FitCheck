package com.fitcheck.common.ai.config;

import com.fitcheck.common.ai.properties.DeepInfraProperties;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DeepInfraProperties.class)
public class DeepInfraEmbeddingConfig {

    private final DeepInfraProperties properties;

    public DeepInfraEmbeddingConfig(DeepInfraProperties properties) {
        this.properties = properties;
    }

    @Bean
    public OpenAiEmbeddingModel deepInfraEmbeddingModel() {
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .baseUrl(properties.baseUrl())
                .apiKey(properties.apiKey())
                .model(properties.embeddingModel())
                .build();

        return OpenAiEmbeddingModel.builder()
                .options(options)
                .build();
    }
}