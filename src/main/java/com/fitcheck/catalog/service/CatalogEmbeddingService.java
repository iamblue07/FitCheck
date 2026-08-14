package com.fitcheck.catalog.service;

import com.fitcheck.catalog.entity.Product;
import com.fitcheck.catalog.repository.ProductRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class CatalogEmbeddingService {

    private static final int BATCH_SIZE = 500;

    private final EmbeddingService embeddingService;
    private final ProductRepository productRepository;

    public void embedPending() {
        int embedded = 0;
        List<Product> chunk = nextChunk();

        while (!chunk.isEmpty()) {
            embedChunk(chunk);
            embedded += chunk.size();
            chunk = nextChunk();
        }

        log.info("Catalog embedding complete: {} products embedded", embedded);
    }

    // Deliberately NOT @Transactional — independent per-chunk commits mean a crash
    // partway through preserves the chunks already saved, and re-running embedPending()
    // later only re-fetches the rows still missing an embedding. A same-class
    // @Transactional call wouldn't work here anyway, since Spring's proxy can't
    // intercept self-invocation.
    private void embedChunk(List<Product> products) {
        List<String> descriptions = products.stream().map(Product::getDescription).toList();
        List<float[]> embeddings = embeddingService.embed(descriptions);

        for (int i = 0; i < products.size(); i++) {
            products.get(i).setTextEmbedding(embeddings.get(i));
        }

        productRepository.saveAll(products);
    }

    private List<Product> nextChunk() {
        return productRepository.findAllByDescriptionIsNotNullAndTextEmbeddingIsNull(Limit.of(BATCH_SIZE));
    }
}