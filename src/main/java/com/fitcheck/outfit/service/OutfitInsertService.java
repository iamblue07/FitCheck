package com.fitcheck.outfit.service;

import com.fitcheck.outfit.domain.CompatibilityScoreBreakdown;
import com.fitcheck.outfit.entity.Outfit;
import com.fitcheck.outfit.enums.OutfitSource;
import com.fitcheck.outfit.repository.OutfitRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
public class OutfitInsertService {

    private final OutfitRepository outfitRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Outfit insert(CompatibilityScoreBreakdown breakdown, String itemSetHash, OutfitSource source) {
        Outfit outfit = Outfit.builder()
                .source(source)
                .compatibilityScore(breakdown.finalScore())
                .colorScore(breakdown.colorScore())
                .layeringScore(breakdown.layeringScore())
                .structuredScore(breakdown.structuredScore())
                .embeddingScore(breakdown.embeddingScore())
                .itemSetHash(itemSetHash)
                .build();
        return outfitRepository.saveAndFlush(outfit);
    }
}