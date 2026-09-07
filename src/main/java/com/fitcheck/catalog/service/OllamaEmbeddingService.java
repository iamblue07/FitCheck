package com.fitcheck.catalog.service;

import com.fitcheck.catalog.entity.Product;
import com.fitcheck.common.ai.EmbeddingVectorTruncator;
import lombok.AllArgsConstructor;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
@ConditionalOnProperty(name = "spring.ai.model.embedding", havingValue = "ollama", matchIfMissing = true)
public class OllamaEmbeddingService implements EmbeddingService {

    private final OllamaEmbeddingModel ollamaEmbeddingModel;

    @Override
    public List<float[]> embed(List<String> texts) {
        return ollamaEmbeddingModel.embed(texts).stream()
                .map(embedding -> EmbeddingVectorTruncator.truncateAndNormalize(embedding, Product.TEXT_EMBEDDING_DIMENSIONS))
                .toList();
    }
}