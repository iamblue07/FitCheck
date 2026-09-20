package com.fitcheck.outfit.service;

import com.fitcheck.catalog.entity.Product;
import com.fitcheck.outfit.domain.CompatibilityScoreBreakdown;
import com.fitcheck.outfit.entity.Outfit;
import com.fitcheck.outfit.enums.OutfitSource;
import com.fitcheck.outfit.repository.OutfitItemRepository;
import com.fitcheck.outfit.repository.OutfitRepository;
import com.fitcheck.outfit.support.OutfitItemAssembler;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@AllArgsConstructor
public class OutfitInsertService {

    private final OutfitRepository outfitRepository;
    private final OutfitItemRepository outfitItemRepository;
    private final OutfitItemAssembler outfitItemAssembler;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Outfit insert(List<Product> selected, CompatibilityScoreBreakdown breakdown, String itemSetHash,
                         OutfitSource source) {
        Outfit outfit = Outfit.builder()
                .source(source)
                .compatibilityScore(breakdown.finalScore())
                .colorScore(breakdown.colorScore())
                .layeringScore(breakdown.layeringScore())
                .structuredScore(breakdown.structuredScore())
                .embeddingScore(breakdown.embeddingScore())
                .itemSetHash(itemSetHash)
                .build();
        outfit = outfitRepository.saveAndFlush(outfit);

        outfitItemRepository.saveAll(outfitItemAssembler.toOutfitItems(outfit, selected));

        return outfit;
    }
}