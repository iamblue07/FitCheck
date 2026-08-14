package com.fitcheck.catalog.controller;

import com.fitcheck.catalog.dto.EnrichmentTriggerResponse;
import com.fitcheck.catalog.entity.Product;
import com.fitcheck.catalog.service.CatalogEnrichmentService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/admin/catalog")
@AllArgsConstructor
public class AdminCatalogController {

    private final CatalogEnrichmentService catalogEnrichmentService;

    @PostMapping("/enrich-next")
    public ResponseEntity<EnrichmentTriggerResponse> enrichNext() {
        Optional<Product> enriched = catalogEnrichmentService.enrichNext();

        EnrichmentTriggerResponse response = enriched
                .map(product -> new EnrichmentTriggerResponse(true, product.getId(), product.getProductDisplayName()))
                .orElseGet(() -> new EnrichmentTriggerResponse(false, null, null));

        return ResponseEntity.ok(response);
    }
}