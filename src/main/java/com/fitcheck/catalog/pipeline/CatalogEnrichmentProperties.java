package com.fitcheck.catalog.pipeline;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "catalog.enrichment")
public record CatalogEnrichmentProperties(
        boolean limitEnabled,
        int maxItems,
        boolean batchEnabled
) {
}

