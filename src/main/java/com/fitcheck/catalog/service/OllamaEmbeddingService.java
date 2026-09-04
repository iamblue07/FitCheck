package com.fitcheck.catalog.service;

import com.fitcheck.catalog.entity.Product;
import lombok.AllArgsConstructor;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
@AllArgsConstructor
@ConditionalOnProperty(name = "spring.ai.model.embedding", havingValue = "ollama", matchIfMissing = true)
public class OllamaEmbeddingService implements EmbeddingService {

    private final OllamaEmbeddingModel ollamaEmbeddingModel;

    @Override
    public List<float[]> embed(List<String> texts) {
        return ollamaEmbeddingModel.embed(texts).stream()
                .map(this::truncateAndNormalize)
                .toList();
    }

    private float[] truncateAndNormalize(float[] fullEmbedding) {
        if (fullEmbedding.length < Product.TEXT_EMBEDDING_DIMENSIONS) {
            throw new IllegalStateException(
                    "Embedding model returned %d dimensions, expected at least %d — check OLLAMA_EMBEDDING_MODEL"
                            .formatted(fullEmbedding.length, Product.TEXT_EMBEDDING_DIMENSIONS));
        }
        return normalize(Arrays.copyOf(fullEmbedding, Product.TEXT_EMBEDDING_DIMENSIONS));
    }

    private float[] normalize(float[] vector) {
        double sumOfSquares = 0.0;
        for (float value : vector) {
            sumOfSquares += (double) value * value;
        }
        float norm = (float) Math.sqrt(sumOfSquares);
        if (norm == 0f) {
            return vector;
        }
        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = vector[i] / norm;
        }
        return normalized;
    }
}