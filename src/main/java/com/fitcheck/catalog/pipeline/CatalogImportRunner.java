package com.fitcheck.catalog.pipeline;

import com.fitcheck.catalog.service.CatalogImportService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Path;

@Slf4j
@Component
@AllArgsConstructor
@EnableConfigurationProperties(CatalogImportProperties.class)
public class CatalogImportRunner implements CommandLineRunner {

    private final CatalogImportProperties properties;
    private final CatalogImportService catalogImportService;

    @Override
    public void run(String... args) {
        if (!properties.enabled()) {
            log.debug("catalog.import.enabled is false; skipping catalog import");
            return;
        }
        if (!StringUtils.hasText(properties.stylesCsvPath()) || !StringUtils.hasText(properties.imagesCsvPath())) {
            log.error("catalog.import.enabled is true but styles/images CSV paths are not configured");
            return;
        }

        log.info("Starting catalog import");
        catalogImportService.importCatalog(Path.of(properties.stylesCsvPath()), Path.of(properties.imagesCsvPath()));
    }
}