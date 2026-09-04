package com.fitcheck.catalog.service;

import com.fitcheck.catalog.entity.Product;
import com.fitcheck.catalog.entity.ProductStyleTag;
import com.fitcheck.catalog.pipeline.CatalogEnrichmentProperties;
import com.fitcheck.catalog.pipeline.ProductEnrichmentResult;
import com.fitcheck.catalog.repository.ProductRepository;
import com.fitcheck.catalog.repository.ProductStyleTagRepository;
import com.fitcheck.common.taxonomy.StyleTag;
import com.fitcheck.common.taxonomy.StyleTagRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "ollama", matchIfMissing = true)
public class CatalogEnrichmentService {

    private final EnrichmentService enrichmentService;
    private final ProductRepository productRepository;
    private final ProductStyleTagRepository productStyleTagRepository;
    private final StyleTagRepository styleTagRepository;
    private final CatalogEnrichmentProperties properties;

    @Transactional
    public Optional<Product> enrichNext() {
        return peekNext(Set.of()).map(this::enrichOne);
    }

    public Optional<Product> peekNext(Set<UUID> excludeIds) {
        if (properties.limitEnabled() && productRepository.countByDescriptionIsNotNull() >= properties.maxItems()) {
            return Optional.empty();
        }
        return excludeIds.isEmpty()
                ? productRepository.findFirstByDescriptionIsNullOrderByCreatedAtAsc()
                : productRepository.findFirstByDescriptionIsNullAndIdNotInOrderByCreatedAtAsc(excludeIds);
    }

    public Product enrichOne(Product product) {
        log.debug("Enriching product {} ({})", product.getId(), product.getProductDisplayName());

        ProductEnrichmentResult result = enrichmentService.enrich(product);

        applyFields(product, result);
        productRepository.save(product);
        productStyleTagRepository.saveAll(resolveStyleTags(product, result.styleTagNames()));

        log.info("Enriched product {} ({})", product.getId(), product.getProductDisplayName());
        return product;
    }


    private void applyFields(Product product, ProductEnrichmentResult result) {
        product.setFit(result.fit());
        product.setSilhouette(result.silhouette());
        product.setPattern(result.pattern());
        product.setMaterialGuess(result.materialGuess());
        product.setFormality(result.formality());
        product.setDescription(result.description());
        product.setOccasion(result.occasion());
        product.setPrimaryColor(result.primaryColor());
        product.setSecondaryColor(result.secondaryColor());
        product.setLayeringRole(result.layeringRole());
        product.setBasePrice(result.basePrice());
        product.setCurrency("EUR");
    }

    private List<ProductStyleTag> resolveStyleTags(Product product, List<String> styleTagNames) {
        List<StyleTag> matchedTags = styleTagRepository.findAllByNameIn(styleTagNames);

        Set<String> matchedNames = matchedTags.stream().map(StyleTag::getName).collect(Collectors.toSet());
        List<String> unmatchedNames = styleTagNames.stream().filter(name -> !matchedNames.contains(name)).toList();
        if (!unmatchedNames.isEmpty()) {
            log.warn("Product {} enrichment returned unknown style tag names: {}", product.getId(), unmatchedNames);
        }

        return matchedTags.stream()
                .map(tag -> toProductStyleTag(product, tag))
                .toList();
    }

    private ProductStyleTag toProductStyleTag(Product product, StyleTag tag) {
        return ProductStyleTag.builder()
                .product(product)
                .styleTag(tag)
                .build();
    }
}