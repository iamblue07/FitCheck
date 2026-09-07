package com.fitcheck.outfit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitcheck.common.exception.ExternalServiceException;
import com.fitcheck.identity.service.UserReferenceQueryService;
import com.fitcheck.outfit.entity.AiPromptQuery;
import com.fitcheck.outfit.entity.AiPromptQueryStatus;
import com.fitcheck.outfit.repository.AiPromptQueryRepository;
import com.fitcheck.outfit.repository.OutfitRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@AllArgsConstructor
public class AiPromptQueryService {

    private final AiPromptQueryRepository aiPromptQueryRepository;
    private final OutfitRepository outfitRepository;
    private final UserReferenceQueryService userReferenceQueryService;
    private final ObjectMapper objectMapper;

    public AiPromptQuery logSuccess(UUID userId, String rawPrompt, Object query, UUID resultingOutfitId) {
        AiPromptQuery aiPromptQuery = AiPromptQuery.builder()
                .user(userReferenceQueryService.getReference(userId))
                .rawPrompt(rawPrompt)
                .structuredQuery(writeAsJson(query))
                .resultingOutfit(outfitRepository.getReferenceById(resultingOutfitId))
                .status(AiPromptQueryStatus.SUCCESS)
                .build();
        return aiPromptQueryRepository.save(aiPromptQuery);
    }

    public AiPromptQuery logFailure(UUID userId, String rawPrompt, String errorMessage) {
        AiPromptQuery aiPromptQuery = AiPromptQuery.builder()
                .user(userReferenceQueryService.getReference(userId))
                .rawPrompt(rawPrompt)
                .status(AiPromptQueryStatus.FAILED)
                .errorMessage(errorMessage)
                .build();
        return aiPromptQueryRepository.save(aiPromptQuery);
    }

    private String writeAsJson(Object query) {
        try {
            return objectMapper.writeValueAsString(query);
        } catch (JsonProcessingException e) {
            throw new ExternalServiceException("Failed to serialize structured prompt query: " + e.getMessage());
        }
    }
}