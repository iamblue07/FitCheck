package com.sewlect.outfit.service;

import com.sewlect.catalog.entity.Product;
import com.sewlect.outfit.domain.CompatibilityScoreBreakdown;
import com.sewlect.outfit.entity.Outfit;
import com.sewlect.outfit.entity.OutfitItem;
import com.sewlect.outfit.enums.OutfitSource;
import com.sewlect.outfit.repository.OutfitItemRepository;
import com.sewlect.outfit.repository.OutfitRepository;
import com.sewlect.outfit.support.OutfitItemAssembler;
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
    private final OutfitInsertService outfitInsertService;
    private final OutfitItemAssembler outfitItemAssembler;

    public Optional<Outfit> findExisting(String itemSetHash) {
        return outfitRepository.findByItemSetHash(itemSetHash);
    }

    @Transactional
    public Outfit saveNew(List<Product> selected, CompatibilityScoreBreakdown breakdown, String itemSetHash,
                          OutfitSource source) {
        return outfitInsertService.insert(selected, breakdown, itemSetHash, source);
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
                outfit = saveNewOutfitRow(candidate);
                newItems.addAll(outfitItemAssembler.toOutfitItems(outfit, candidate.products()));
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

    public record PersistenceCandidate(List<Product> products, CompatibilityScoreBreakdown breakdown,
                                       String itemSetHash, OutfitSource source) {
    }
}