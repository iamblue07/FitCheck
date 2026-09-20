package com.sewlect.catalog.config;

import com.sewlect.catalog.properties.CatalogEnrichmentProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CatalogEnrichmentProperties.class)
public class CatalogPipelineConfig {
}