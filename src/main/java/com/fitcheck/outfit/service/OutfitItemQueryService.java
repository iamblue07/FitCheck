package com.fitcheck.outfit.service;

import com.fitcheck.outfit.dto.OutfitItemView;
import com.fitcheck.outfit.entity.OutfitItem;
import com.fitcheck.outfit.repository.OutfitItemRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class OutfitItemQueryService {

    private final OutfitItemRepository outfitItemRepository;

    public List<UUID> findProductIds(UUID outfitId) {
        return outfitItemRepository.findDistinctProductIdByOutfitId(outfitId);
    }

    public List<OutfitItemView> findItemViews(UUID outfitId) {
        return outfitItemRepository.findByOutfitId(outfitId).stream()
                .map(item -> new OutfitItemView(
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
}