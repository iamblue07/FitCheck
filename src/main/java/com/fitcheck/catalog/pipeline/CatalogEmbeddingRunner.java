package com.fitcheck.catalog.pipeline;

import com.fitcheck.catalog.entity.Product;
import com.fitcheck.catalog.service.CatalogEmbeddingService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@AllArgsConstructor
@EnableConfigurationProperties(CatalogEmbeddingProperties.class)
public class CatalogEmbeddingRunner implements CommandLineRunner {

    private final CatalogEmbeddingProperties properties;
    private final CatalogEmbeddingService catalogEmbeddingService;

    @Override
    public void run(String... args) {
        if (!properties.batchEnabled()) {
            log.debug("catalog.embedding.batch-enabled is false; skipping embedding batch");
            return;
        }

        log.info("Starting catalog embedding batch");
        Set<UUID> failedIds = new HashSet<>();
        int embeddedCount = 0;

        List<Product> chunk = catalogEmbeddingService.nextChunk(failedIds);
        while (!chunk.isEmpty()) {
            try {
                catalogEmbeddingService.embedChunk(chunk);
                embeddedCount += chunk.size();
                log.info("Embedding progress: {} completed", embeddedCount);
            } catch (Exception e) {
                List<UUID> chunkIds = chunk.stream().map(Product::getId).toList();
                log.error("Failed to embed chunk of {} products — skipping for this run: {}", chunk.size(), chunkIds, e);
                failedIds.addAll(chunkIds);
            }
            chunk = catalogEmbeddingService.nextChunk(failedIds);
        }

        log.info("Catalog embedding complete: {} embedded, {} failed", embeddedCount, failedIds.size());
    }
}