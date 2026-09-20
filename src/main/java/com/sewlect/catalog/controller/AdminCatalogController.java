package com.sewlect.catalog.controller;

import com.sewlect.catalog.dto.EnrichmentTriggerResponse;
import com.sewlect.catalog.entity.Product;
import com.sewlect.catalog.service.CatalogEnrichmentService;
import com.sewlect.common.openapi.annotation.StandardApiErrors;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/admin/catalog")
@AllArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "ollama", matchIfMissing = true)
@Tag(name = "Admin - catalog", description = "Operator-only catalog enrichment; requires the ADMIN role")
public class AdminCatalogController {

    private final CatalogEnrichmentService catalogEnrichmentService;

    @Operation(summary = "Enrich the next un-enriched product; returns enriched=false when nothing is left")
    @StandardApiErrors
    @PostMapping("/enrich-next")
    public ResponseEntity<EnrichmentTriggerResponse> enrichNext() {
        Optional<Product> enriched = catalogEnrichmentService.enrichNext();

        EnrichmentTriggerResponse response = enriched
                .map(product -> new EnrichmentTriggerResponse(true, product.getId(), product.getProductDisplayName()))
                .orElseGet(() -> new EnrichmentTriggerResponse(false, null, null));

        return ResponseEntity.ok(response);
    }
}