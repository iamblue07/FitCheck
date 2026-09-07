package com.fitcheck.outfit.service;

import com.fitcheck.catalog.entity.Product;
import com.fitcheck.common.ai.EmbeddingVectorTruncator;
import lombok.AllArgsConstructor;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.data.domain.Vector;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class PromptQueryEmbeddingService {

    private final OpenAiEmbeddingModel openAiEmbeddingModel;

    public Vector embed(String text) {
        float[] rawEmbedding = openAiEmbeddingModel.embed(text);
        return Vector.of(EmbeddingVectorTruncator.truncateAndNormalize(rawEmbedding, Product.TEXT_EMBEDDING_DIMENSIONS));
    }
}