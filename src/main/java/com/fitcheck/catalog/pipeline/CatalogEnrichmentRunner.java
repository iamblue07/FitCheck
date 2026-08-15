package com.fitcheck.catalog.pipeline;

import com.fitcheck.catalog.entity.Product;
import com.fitcheck.catalog.service.CatalogEnrichmentService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@AllArgsConstructor
public class CatalogEnrichmentRunner implements CommandLineRunner {

    private final CatalogEnrichmentProperties properties;
    private final CatalogEnrichmentService catalogEnrichmentService;

    @Override
    public void run(String... args) {
        if (!properties.batchEnabled()) {
            return;
        }

        Set<UUID> failedIds = new HashSet<>();
        int enrichedCount = 0;

        Optional<Product> next;
        while ((next = catalogEnrichmentService.peekNext(failedIds)).isPresent()) {
            Product product = next.get();
            try {
                catalogEnrichmentService.enrichOne(product);
                enrichedCount++;
                if (enrichedCount % 50 == 0) {
                    log.info("Enrichment progress: {} completed", enrichedCount);
                }
            } catch (Exception e) {
                log.error("Failed to enrich product {} — skipping for this run", product.getId(), e);
                failedIds.add(product.getId());
            }
        }

        log.info("Enrichment batch complete: {} enriched, {} failed", enrichedCount, failedIds.size());
    }
}