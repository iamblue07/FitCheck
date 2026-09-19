package com.fitcheck.outfit.support;

import com.fitcheck.outfit.domain.CompatibilityScoreBreakdown;
import com.fitcheck.outfit.domain.OutfitItemView;
import com.fitcheck.outfit.dto.OutfitResponse;
import com.fitcheck.outfit.entity.Outfit;
import com.fitcheck.outfit.service.OutfitItemQueryService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@AllArgsConstructor
public class OutfitResponseAssembler {

    private final OutfitItemQueryService outfitItemQueryService;

    public OutfitResponse toResponse(Outfit outfit) {
        return new OutfitResponse(
                outfit.getId(),
                toBreakdown(outfit),
                outfitItemQueryService.sumBasePrice(outfit.getId()),
                outfitItemQueryService.findItemViews(outfit.getId()));
    }

    public List<OutfitResponse> toResponses(List<Outfit> outfits) {
        if (outfits.isEmpty()) {
            return List.of();
        }

        List<UUID> outfitIds = outfits.stream().map(Outfit::getId).toList();
        Map<UUID, List<OutfitItemView>> itemViewsByOutfitId = outfitItemQueryService.findItemViewsForOutfits(outfitIds);
        Map<UUID, BigDecimal> totalPriceByOutfitId = outfitItemQueryService.sumBasePriceForOutfits(outfitIds);

        return outfits.stream()
                .map(outfit -> new OutfitResponse(
                        outfit.getId(),
                        toBreakdown(outfit),
                        totalPriceByOutfitId.get(outfit.getId()),
                        itemViewsByOutfitId.get(outfit.getId())))
                .toList();
    }

    private CompatibilityScoreBreakdown toBreakdown(Outfit outfit) {
        return new CompatibilityScoreBreakdown(
                outfit.getColorScore(),
                outfit.getLayeringScore(),
                outfit.getStructuredScore(),
                outfit.getEmbeddingScore(),
                outfit.getCompatibilityScore());
    }
}