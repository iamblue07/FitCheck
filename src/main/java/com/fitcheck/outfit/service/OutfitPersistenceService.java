package com.fitcheck.outfit.service;

import com.fitcheck.catalog.entity.Product;
import com.fitcheck.outfit.entity.Outfit;
import com.fitcheck.outfit.entity.OutfitItem;
import com.fitcheck.outfit.entity.OutfitSource;
import com.fitcheck.outfit.repository.OutfitItemRepository;
import com.fitcheck.outfit.repository.OutfitRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class OutfitPersistenceService {

    private final OutfitRepository outfitRepository;
    private final OutfitItemRepository outfitItemRepository;

    public Optional<Outfit> findExisting(String itemSetHash) {
        return outfitRepository.findByItemSetHash(itemSetHash);
    }

    @Transactional
    public Outfit saveNew(List<Product> selected, BigDecimal compatibilityScore, String itemSetHash) {
        Outfit outfit = Outfit.builder()
                .source(OutfitSource.PROFILE_GENERATED)
                .compatibilityScore(compatibilityScore)
                .itemSetHash(itemSetHash)
                .build();
        outfit = outfitRepository.saveAndFlush(outfit);

        List<OutfitItem> items = new ArrayList<>();
        for (Product product : selected) {
            items.add(buildOutfitItem(outfit, product));
        }
        outfitItemRepository.saveAll(items);

        return outfit;
    }

    private OutfitItem buildOutfitItem(Outfit outfit, Product product) {
        return OutfitItem.builder()
                .outfit(outfit)
                .product(product)
                .slot(product.getGarmentRole())
                .build();
    }
}