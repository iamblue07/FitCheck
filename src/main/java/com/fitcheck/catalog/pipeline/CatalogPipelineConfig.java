package com.fitcheck.catalog.pipeline;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.util.Random;

@Configuration
@EnableConfigurationProperties(CatalogEnrichmentProperties.class)
public class CatalogPipelineConfig {

    @Bean
    public HttpClient catalogHttpClient() {
        return HttpClient.newHttpClient();
    }
}