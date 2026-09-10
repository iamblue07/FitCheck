package com.fitcheck.catalog.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "catalog.enrichment")
public record CatalogEnrichmentProperties(
        boolean limitEnabled,
        int maxItems,
        boolean batchEnabled
) {
}

