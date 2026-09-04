package com.fitcheck.catalog.service;

import com.fitcheck.catalog.entity.Product;
import com.fitcheck.catalog.config.CatalogEmbeddingProperties;
import com.fitcheck.catalog.repository.ProductRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@AllArgsConstructor
@ConditionalOnProperty(name = "spring.ai.model.embedding", havingValue = "ollama", matchIfMissing = true)
public class CatalogEmbeddingService {

    private final CatalogEmbeddingProperties properties;
    private final EmbeddingService embeddingService;
    private final ProductRepository productRepository;

    public List<Product> nextChunk(Set<UUID> excludeIds) {
        if (properties.limitEnabled() && productRepository.countByTextEmbeddingIsNotNull() >= properties.maxItems()) {
            return List.of();
        }
        return excludeIds.isEmpty()
                ? productRepository.findAllByDescriptionIsNotNullAndTextEmbeddingIsNull(Limit.of(properties.chunkSize()))
                : productRepository.findAllByDescriptionIsNotNullAndTextEmbeddingIsNullAndIdNotIn(excludeIds, Limit.of(properties.chunkSize()));
    }

    public void embedChunk(List<Product> products) {
        List<String> descriptions = products.stream().map(Product::getDescription).toList();
        List<float[]> embeddings = embeddingService.embed(descriptions);

        for (int i = 0; i < products.size(); i++) {
            products.get(i).setTextEmbedding(embeddings.get(i));
        }

        productRepository.saveAll(products);
    }
}