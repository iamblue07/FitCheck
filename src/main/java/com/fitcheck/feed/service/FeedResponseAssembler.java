package com.fitcheck.feed.service;

import com.fitcheck.feed.dto.CompatibilityBreakdownResponse;
import com.fitcheck.feed.dto.FeedItemResponse;
import com.fitcheck.feed.dto.FeedOutfitItemResponse;
import com.fitcheck.feed.entity.FeedEntry;
import com.fitcheck.outfit.dto.OutfitItemView;
import com.fitcheck.outfit.entity.Outfit;
import com.fitcheck.outfit.service.OutfitItemQueryService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@AllArgsConstructor
public class FeedResponseAssembler {

    private final OutfitItemQueryService outfitItemQueryService;

    public FeedItemResponse toResponse(FeedEntry entry) {
        Outfit outfit = entry.getOutfit();

        List<FeedOutfitItemResponse> items = outfitItemQueryService.findItemViews(outfit.getId()).stream()
                .map(this::toItemResponse)
                .toList();

        BigDecimal totalPrice = items.stream()
                .map(FeedOutfitItemResponse::basePrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        CompatibilityBreakdownResponse breakdown = new CompatibilityBreakdownResponse(
                outfit.getColorScore(),
                outfit.getLayeringScore(),
                outfit.getStructuredScore(),
                outfit.getEmbeddingScore(),
                outfit.getCompatibilityScore());

        return new FeedItemResponse(outfit.getId(), breakdown, entry.getRankScore(), totalPrice, items);
    }

    private FeedOutfitItemResponse toItemResponse(OutfitItemView view) {
        return new FeedOutfitItemResponse(
                view.productId(), view.productDisplayName(), view.imageUrl(), view.basePrice(), view.slot());
    }
}