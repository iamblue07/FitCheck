package com.sewlect.feed.support;

import com.sewlect.feed.dto.CompatibilityBreakdownResponse;
import com.sewlect.feed.dto.FeedItemResponse;
import com.sewlect.feed.dto.FeedOutfitItemResponse;
import com.sewlect.feed.entity.FeedEntry;
import com.sewlect.outfit.domain.OutfitItemView;
import com.sewlect.outfit.entity.Outfit;
import com.sewlect.outfit.service.OutfitItemQueryService;
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
                view.itemId(), view.productId(), view.productDisplayName(), view.imageUrl(), view.basePrice(), view.slot());
    }
}