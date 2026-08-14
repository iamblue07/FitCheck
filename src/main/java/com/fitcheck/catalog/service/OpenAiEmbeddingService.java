package com.fitcheck.catalog.service;

import lombok.AllArgsConstructor;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class OpenAiEmbeddingService implements EmbeddingService {

    private final OpenAiEmbeddingModel openAiEmbeddingModel;

    @Override
    public List<float[]> embed(List<String> texts) {
        return openAiEmbeddingModel.embed(texts);
    }
}