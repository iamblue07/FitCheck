package com.fitcheck.catalog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "catalog.embedding")
public record CatalogEmbeddingProperties(
        boolean batchEnabled,
        int maxItems,
        boolean limitEnabled,
        int chunkSize
) {}
