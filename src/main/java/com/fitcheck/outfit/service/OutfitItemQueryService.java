package com.fitcheck.outfit.service;

import com.fitcheck.catalog.entity.Product;
import com.fitcheck.common.exception.ResourceNotFoundException;
import com.fitcheck.outfit.dto.OutfitItemView;
import com.fitcheck.outfit.entity.OutfitItem;
import com.fitcheck.outfit.repository.OutfitItemRepository;
import com.fitcheck.outfit.repository.OutfitRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class OutfitItemQueryService {

    private final OutfitItemRepository outfitItemRepository;
    private final OutfitRepository outfitRepository;

    public List<UUID> findProductIds(UUID outfitId) {
        return outfitItemRepository.findDistinctProductIdByOutfitId(outfitId);
    }

    public List<OutfitItemView> findItemViews(UUID outfitId) {
        return outfitItemRepository.findByOutfitId(outfitId).stream()
                .map(item -> new OutfitItemView(
                        item.getId(),
                        item.getProduct().getId(),
                        item.getProduct().getProductDisplayName(),
                        item.getProduct().getImageUrl(),
                        item.getProduct().getBasePrice(),
                        item.getSlot()))
                .toList();
    }

    public BigDecimal sumBasePrice(UUID outfitId) {
        return outfitItemRepository.sumBasePriceByOutfitId(outfitId);
    }

    public OutfitItemContext loadContext(UUID outfitId, UUID itemId) {
        outfitRepository.findById(outfitId)
                .orElseThrow(() -> new ResourceNotFoundException("Outfit not found: " + outfitId));

        List<OutfitItem> items = outfitItemRepository.findByOutfitId(outfitId);

        OutfitItem targetItem = items.stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Outfit item " + itemId + " not found in outfit " + outfitId));

        List<Product> otherProducts = items.stream()
                .filter(item -> !item.getId().equals(itemId))
                .map(OutfitItem::getProduct)
                .toList();

        return new OutfitItemContext(targetItem, otherProducts);
    }

    public record OutfitItemContext(OutfitItem targetItem, List<Product> otherProducts) {
    }
}