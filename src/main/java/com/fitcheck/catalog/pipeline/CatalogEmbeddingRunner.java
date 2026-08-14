package com.fitcheck.catalog.pipeline;

import com.fitcheck.catalog.service.CatalogEmbeddingService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
@EnableConfigurationProperties(CatalogEmbeddingProperties.class)
public class CatalogEmbeddingRunner implements CommandLineRunner {

    private final CatalogEmbeddingProperties properties;
    private final CatalogEmbeddingService catalogEmbeddingService;

    @Override
    public void run(String... args) {
        if (!properties.enabled()) {
            log.debug("catalog.embedding.enabled is false; skipping embedding batch");
            return;
        }

        log.info("Starting catalog embedding batch");
        catalogEmbeddingService.embedPending();
    }
}