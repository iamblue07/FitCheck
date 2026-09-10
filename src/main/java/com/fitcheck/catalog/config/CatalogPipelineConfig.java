package com.fitcheck.catalog.config;

import com.fitcheck.catalog.properties.CatalogEnrichmentProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(CatalogEnrichmentProperties.class)
public class CatalogPipelineConfig {

    @Bean
    public HttpClient catalogHttpClient() {
        return HttpClient.newHttpClient();
    }
}