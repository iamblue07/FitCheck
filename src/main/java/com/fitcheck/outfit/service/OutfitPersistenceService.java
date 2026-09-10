package com.fitcheck.outfit.service;

import com.fitcheck.catalog.entity.Product;
import com.fitcheck.outfit.domain.CompatibilityScoreBreakdown;
import com.fitcheck.outfit.entity.Outfit;
import com.fitcheck.outfit.entity.OutfitItem;
import com.fitcheck.outfit.enums.OutfitSource;
import com.fitcheck.outfit.repository.OutfitItemRepository;
import com.fitcheck.outfit.repository.OutfitRepository;
import lombok.AllArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    public Outfit saveNew(List<Product> selected, CompatibilityScoreBreakdown breakdown, String itemSetHash,
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

        List<OutfitItem> items = new ArrayList<>();
        for (Product product : selected) {
            items.add(buildOutfitItem(outfit, product));
        }
        outfitItemRepository.saveAll(items);

        return outfit;
    }

    @Transactional
    public Outfit saveOrReuse(List<Product> selected, CompatibilityScoreBreakdown breakdown, String itemSetHash,
                              OutfitSource source) {
        Optional<Outfit> existing = findExisting(itemSetHash);
        if (existing.isPresent()) {
            return existing.get();
        }

        try {
            return saveNew(selected, breakdown, itemSetHash, source);
        } catch (DataIntegrityViolationException e) {
            return findExisting(itemSetHash)
                    .orElseThrow(() -> new IllegalStateException(
                            "Outfit insert failed on unique constraint but no existing row found for hash "
                                    + itemSetHash, e));
        }
    }

    @Transactional
    public List<Outfit> saveOrReuseBatch(List<PersistenceCandidate> candidates) {
        List<String> hashes = candidates.stream().map(PersistenceCandidate::itemSetHash).toList();
        Map<String, Outfit> resolvedByHash = new HashMap<>();
        for (Outfit outfit : outfitRepository.findByItemSetHashIn(hashes)) {
            resolvedByHash.put(outfit.getItemSetHash(), outfit);
        }

        List<OutfitItem> newItems = new ArrayList<>();
        List<Outfit> results = new ArrayList<>(candidates.size());

        for (PersistenceCandidate candidate : candidates) {
            Outfit outfit = resolvedByHash.get(candidate.itemSetHash());
            if (outfit == null) {
                try {
                    outfit = saveNewOutfitRow(candidate);
                    newItems.addAll(buildOutfitItems(outfit, candidate.products()));
                } catch (DataIntegrityViolationException e) {
                    outfit = findExisting(candidate.itemSetHash())
                            .orElseThrow(() -> new IllegalStateException(
                                    "Outfit insert failed on unique constraint but no existing row found for hash "
                                            + candidate.itemSetHash(), e));
                }
                resolvedByHash.put(candidate.itemSetHash(), outfit);
            }
            results.add(outfit);
        }

        outfitItemRepository.saveAll(newItems);

        return results;
    }

    private Outfit saveNewOutfitRow(PersistenceCandidate candidate) {
        Outfit outfit = Outfit.builder()
                .source(candidate.source())
                .compatibilityScore(candidate.breakdown().finalScore())
                .colorScore(candidate.breakdown().colorScore())
                .layeringScore(candidate.breakdown().layeringScore())
                .structuredScore(candidate.breakdown().structuredScore())
                .embeddingScore(candidate.breakdown().embeddingScore())
                .itemSetHash(candidate.itemSetHash())
                .build();
        return outfitRepository.saveAndFlush(outfit);
    }

    private List<OutfitItem> buildOutfitItems(Outfit outfit, List<Product> products) {
        List<OutfitItem> items = new ArrayList<>();
        for (Product product : products) {
            items.add(buildOutfitItem(outfit, product));
        }
        return items;
    }

    private OutfitItem buildOutfitItem(Outfit outfit, Product product) {
        return OutfitItem.builder()
                .outfit(outfit)
                .product(product)
                .slot(product.getGarmentRole())
                .build();
    }

    public record PersistenceCandidate(List<Product> products, CompatibilityScoreBreakdown breakdown,
                                       String itemSetHash, OutfitSource source) {
    }
}