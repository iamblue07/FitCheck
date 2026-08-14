package com.fitcheck.catalog.pipeline;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "catalog.embedding")
public record CatalogEmbeddingProperties(
        boolean enabled
) {}
