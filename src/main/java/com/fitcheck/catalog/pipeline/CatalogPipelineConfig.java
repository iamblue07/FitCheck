package com.fitcheck.catalog.pipeline;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Random;

@Configuration
public class CatalogPipelineConfig {

    @Bean
    public Random catalogImportRandom() {
        return new Random();
    }
}