package com.sewlect.outfit.support;

import com.sewlect.outfit.domain.CompatibilityScoreBreakdown;
import com.sewlect.outfit.domain.OutfitItemView;
import com.sewlect.outfit.dto.OutfitResponse;
import com.sewlect.outfit.entity.Outfit;
import com.sewlect.outfit.service.OutfitItemQueryService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@AllArgsConstructor
public class OutfitResponseAssembler {

    private final OutfitItemQueryService outfitItemQueryService;
    private final OutfitViewCache outfitViewCache;

    public OutfitResponse toResponse(Outfit outfit) {
        UUID outfitId = outfit.getId();
        OutfitResponse cached = outfitViewCache.getAll(List.of(outfitId)).get(outfitId);
        if (cached != null) {
            return cached;
        }

        OutfitResponse assembled = new OutfitResponse(
                outfitId,
                toBreakdown(outfit),
                outfitItemQueryService.sumBasePrice(outfitId),
                outfitItemQueryService.findItemViews(outfitId));
        outfitViewCache.putAll(Map.of(outfitId, assembled));
        return assembled;
    }

    public List<OutfitResponse> toResponses(List<Outfit> outfits) {
        if (outfits.isEmpty()) {
            return List.of();
        }

        List<UUID> outfitIds = outfits.stream().map(Outfit::getId).toList();
        Map<UUID, OutfitResponse> cached = outfitViewCache.getAll(outfitIds);

        List<Outfit> misses = outfits.stream()
                .filter(outfit -> !cached.containsKey(outfit.getId()))
                .toList();
        Map<UUID, OutfitResponse> assembled = assemble(misses);
        outfitViewCache.putAll(assembled);

        return outfits.stream()
                .map(outfit -> cached.containsKey(outfit.getId())
                        ? cached.get(outfit.getId())
                        : assembled.get(outfit.getId()))
                .toList();
    }

    private Map<UUID, OutfitResponse> assemble(List<Outfit> outfits) {
        if (outfits.isEmpty()) {
            return Map.of();
        }

        List<UUID> outfitIds = outfits.stream().map(Outfit::getId).toList();
        Map<UUID, List<OutfitItemView>> itemViewsByOutfitId = outfitItemQueryService.findItemViewsForOutfits(outfitIds);
        Map<UUID, BigDecimal> totalPriceByOutfitId = outfitItemQueryService.sumBasePriceForOutfits(outfitIds);

        Map<UUID, OutfitResponse> assembled = new HashMap<>();
        for (Outfit outfit : outfits) {
            assembled.put(outfit.getId(), new OutfitResponse(
                    outfit.getId(),
                    toBreakdown(outfit),
                    totalPriceByOutfitId.get(outfit.getId()),
                    itemViewsByOutfitId.get(outfit.getId())));
        }
        return assembled;
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