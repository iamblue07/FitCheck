package com.fitcheck.catalog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "catalog.import")
public record CatalogImportProperties (
        boolean enabled,
        String stylesCsvPath,
        String imagesCsvPath
) {
}
