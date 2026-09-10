package com.fitcheck.outfit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitcheck.common.exception.ExternalServiceException;
import com.fitcheck.identity.service.UserReferenceQueryService;
import com.fitcheck.outfit.entity.AiPromptQuery;
import com.fitcheck.outfit.entity.AiPromptQueryOutfit;
import com.fitcheck.outfit.enums.AiPromptQueryStatus;
import com.fitcheck.outfit.repository.AiPromptQueryOutfitRepository;
import com.fitcheck.outfit.repository.AiPromptQueryRepository;
import com.fitcheck.outfit.repository.OutfitRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class AiPromptQueryService {

    private final AiPromptQueryRepository aiPromptQueryRepository;
    private final AiPromptQueryOutfitRepository aiPromptQueryOutfitRepository;
    private final OutfitRepository outfitRepository;
    private final UserReferenceQueryService userReferenceQueryService;
    private final ObjectMapper objectMapper;

    public AiPromptQuery logSuccess(UUID userId, String rawPrompt, Object query, boolean matchProfile,
                                    List<UUID> resultingOutfitIds) {
        AiPromptQuery aiPromptQuery = AiPromptQuery.builder()
                .user(userReferenceQueryService.getReference(userId))
                .rawPrompt(rawPrompt)
                .structuredQuery(writeAsJson(query))
                .matchProfile(matchProfile)
                .status(AiPromptQueryStatus.SUCCESS)
                .build();
        aiPromptQueryRepository.save(aiPromptQuery);

        for (int rank = 0; rank < resultingOutfitIds.size(); rank++) {
            AiPromptQueryOutfit aiPromptQueryOutfit = AiPromptQueryOutfit.builder()
                    .aiPromptQuery(aiPromptQuery)
                    .outfit(outfitRepository.getReferenceById(resultingOutfitIds.get(rank)))
                    .rank(rank)
                    .build();
            aiPromptQueryOutfitRepository.save(aiPromptQueryOutfit);
        }

        return aiPromptQuery;
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