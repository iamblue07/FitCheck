package com.fitcheck.outfit.service;

import com.fitcheck.catalog.entity.Product;
import com.fitcheck.common.exception.ResourceNotFoundException;
import com.fitcheck.common.taxonomy.enums.GarmentRole;
import com.fitcheck.outfit.domain.OutfitItemView;
import com.fitcheck.outfit.entity.Outfit;
import com.fitcheck.outfit.entity.OutfitItem;
import com.fitcheck.outfit.repository.OutfitItemRepository;
import com.fitcheck.outfit.repository.OutfitRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@AllArgsConstructor
public class OutfitItemQueryService {

    private static final List<GarmentRole> TRY_ON_ORDER = List.of(
            GarmentRole.FULL_BODY,
            GarmentRole.BOTTOM,
            GarmentRole.TOP,
            GarmentRole.OUTERWEAR,
            GarmentRole.FOOTWEAR,
            GarmentRole.ACCESSORY);

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

    public Map<UUID, List<OutfitItemView>> findItemViewsForOutfits(List<UUID> outfitIds) {
        Map<UUID, List<OutfitItemView>> viewsByOutfitId = new HashMap<>();
        for (UUID outfitId : outfitIds) {
            viewsByOutfitId.put(outfitId, new ArrayList<>());
        }
        for (OutfitItem item : outfitItemRepository.findByOutfitIdIn(outfitIds)) {
            viewsByOutfitId.get(item.getOutfit().getId()).add(new OutfitItemView(
                    item.getId(),
                    item.getProduct().getId(),
                    item.getProduct().getProductDisplayName(),
                    item.getProduct().getImageUrl(),
                    item.getProduct().getBasePrice(),
                    item.getSlot()));
        }
        return viewsByOutfitId;
    }

    public BigDecimal sumBasePrice(UUID outfitId) {
        return outfitItemRepository.sumBasePriceByOutfitId(outfitId);
    }

    public Map<UUID, BigDecimal> sumBasePriceForOutfits(List<UUID> outfitIds) {
        Map<UUID, BigDecimal> totalsByOutfitId = new HashMap<>();
        for (UUID outfitId : outfitIds) {
            totalsByOutfitId.put(outfitId, BigDecimal.ZERO);
        }
        for (OutfitItemRepository.OutfitBasePriceTotal total : outfitItemRepository.sumBasePriceByOutfitIdIn(outfitIds)) {
            totalsByOutfitId.put(total.getOutfitId(), total.getTotalBasePrice());
        }
        return totalsByOutfitId;
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

    public List<Product> findProductsForTryon(UUID outfitId) {
        outfitRepository.findById(outfitId)
                .orElseThrow(() -> new ResourceNotFoundException("Outfit not found: " + outfitId));

        return outfitItemRepository.findByOutfitId(outfitId).stream()
                .sorted(Comparator.comparingInt(item -> TRY_ON_ORDER.indexOf(item.getSlot())))
                .map(OutfitItem::getProduct)
                .toList();
    }

    public Outfit getReference(UUID outfitId) {
        return outfitRepository.getReferenceById(outfitId);
    }

    public record OutfitItemContext(OutfitItem targetItem, List<Product> otherProducts) {
    }
}