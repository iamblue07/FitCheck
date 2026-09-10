package com.fitcheck.outfit.service;

import com.fitcheck.catalog.entity.Product;
import com.fitcheck.catalog.service.ProductSearchService;
import com.fitcheck.common.exception.ExternalServiceException;
import com.fitcheck.identity.entity.UserProfile;
import com.fitcheck.identity.service.UserProfileQueryService;
import com.fitcheck.outfit.properties.OutfitDiversityProperties;
import com.fitcheck.outfit.properties.OutfitPromptProperties;
import com.fitcheck.outfit.domain.CompatibilityScoreBreakdown;
import com.fitcheck.outfit.domain.OutfitBlueprint;
import com.fitcheck.outfit.domain.OutfitItemView;
import com.fitcheck.outfit.dto.OutfitResponse;
import com.fitcheck.outfit.domain.PromptInferredQuery;
import com.fitcheck.outfit.domain.SlotDescription;
import com.fitcheck.outfit.domain.StructuredPromptQuery;
import com.fitcheck.outfit.entity.Outfit;
import com.fitcheck.outfit.enums.OutfitSource;
import com.fitcheck.outfit.support.BudgetCeilingResolver;
import com.fitcheck.outfit.support.OutfitGenderFilterResolver;
import com.fitcheck.outfit.support.OutfitItemSetHasher;
import lombok.AllArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.SearchResult;
import org.springframework.data.domain.SearchResults;
import org.springframework.data.domain.Vector;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

@Service
@AllArgsConstructor
@EnableConfigurationProperties(OutfitPromptProperties.class)
public class PromptOutfitGenerationService {

    private final PromptExtractionService promptExtractionService;
    private final PromptQueryEmbeddingService promptQueryEmbeddingService;
    private final ProductSearchService productSearchService;
    private final OutfitCompatibilityScorer outfitCompatibilityScorer;
    private final OutfitGenderFilterResolver outfitGenderFilterResolver;
    private final OutfitItemSetHasher outfitItemSetHasher;
    private final OutfitPersistenceService outfitPersistenceService;
    private final OutfitItemQueryService outfitItemQueryService;
    private final UserProfileQueryService userProfileQueryService;
    private final AiPromptQueryService aiPromptQueryService;
    private final BudgetCeilingResolver budgetCeilingResolver;
    private final OutfitPromptProperties properties;
    private final OutfitDiversityProperties diversityProperties;

    public List<OutfitResponse> generate(UUID userId, String rawPrompt, boolean matchProfile) {
        if (matchProfile) {
            StructuredPromptQuery query = extractOrLogFailure(userId, rawPrompt, promptExtractionService::extract);

            UserProfile profile = userProfileQueryService.getById(userId);
            Set<String> genders = outfitGenderFilterResolver.allowedGenders(profile.getSex());
            BigDecimal priceCeiling = budgetCeilingResolver.resolve(profile.getAverageBudgetPerOutfit());

            return buildResponses(userId, rawPrompt, query.blueprints(), genders, priceCeiling, query, matchProfile);
        }

        PromptInferredQuery query = extractOrLogFailure(
                userId, rawPrompt, promptExtractionService::extractWithInferredFilters);

        Set<String> genders = query.genders();
        BigDecimal priceCeiling = budgetCeilingResolver.resolve(query.budget());

        return buildResponses(userId, rawPrompt, query.blueprints(), genders, priceCeiling, query, matchProfile);
    }

    private <T> T extractOrLogFailure(UUID userId, String rawPrompt, Function<String, T> extractor) {
        try {
            return extractor.apply(rawPrompt);
        } catch (RuntimeException e) {
            aiPromptQueryService.logFailure(userId, rawPrompt, e.getMessage());
            throw e;
        }
    }

    private List<OutfitResponse> buildResponses(UUID userId, String rawPrompt, List<OutfitBlueprint> blueprints,
                                                Set<String> genders, BigDecimal priceCeiling, Object loggedQuery,
                                                boolean matchProfile) {
        List<BlueprintResult> pool = new ArrayList<>();
        for (OutfitBlueprint blueprint : blueprints) {
            if (pool.size() >= properties.generationPoolSize()) {
                break;
            }
            collectCombinationsFor(blueprint, genders, priceCeiling, pool);
        }

        if (pool.isEmpty()) {
            String errorMessage = "No viable product combination was found for any extracted blueprint";
            aiPromptQueryService.logFailure(userId, rawPrompt, errorMessage);
            throw new ExternalServiceException(errorMessage);
        }

        List<BlueprintResult> sortedPool = pool.stream()
                .sorted(Comparator.comparing((BlueprintResult result) -> result.breakdown().finalScore()).reversed())
                .toList();

        List<WinningOutfit> winners = selectTopUniqueCombinations(sortedPool);

        List<OutfitPersistenceService.PersistenceCandidate> candidates = winners.stream()
                .map(winner -> new OutfitPersistenceService.PersistenceCandidate(
                        winner.products(), winner.breakdown(), winner.itemSetHash(), OutfitSource.AI_PROMPT))
                .toList();
        List<Outfit> outfits = outfitPersistenceService.saveOrReuseBatch(candidates);

        List<UUID> outfitIds = outfits.stream().map(Outfit::getId).toList();
        Map<UUID, List<OutfitItemView>> itemViewsByOutfitId = outfitItemQueryService.findItemViewsForOutfits(outfitIds);
        Map<UUID, BigDecimal> totalPriceByOutfitId = outfitItemQueryService.sumBasePriceForOutfits(outfitIds);

        List<OutfitResponse> responses = new ArrayList<>();
        for (int i = 0; i < winners.size(); i++) {
            Outfit outfit = outfits.get(i);
            WinningOutfit winner = winners.get(i);
            responses.add(new OutfitResponse(outfit.getId(), winner.breakdown(),
                    totalPriceByOutfitId.get(outfit.getId()), itemViewsByOutfitId.get(outfit.getId())));
        }

        aiPromptQueryService.logSuccess(userId, rawPrompt, loggedQuery, matchProfile,
                responses.stream().map(OutfitResponse::outfitId).toList());

        return responses;
    }

    private List<WinningOutfit> selectTopUniqueCombinations(List<BlueprintResult> sortedPool) {
        List<WinningOutfit> winners = new ArrayList<>();
        Set<String> seenHashes = new HashSet<>();
        Map<UUID, Integer> productUsageCounts = new HashMap<>();
        int maxRepetitions = diversityProperties.maxProductRepetitions();

        for (BlueprintResult candidate : sortedPool) {
            if (winners.size() >= properties.batchSize()) {
                break;
            }
            String hash = outfitItemSetHasher.hash(candidate.products());
            if (seenHashes.contains(hash)) {
                continue;
            }
            if (!withinRepetitionCap(candidate.products(), productUsageCounts, maxRepetitions)) {
                continue;
            }
            seenHashes.add(hash);
            recordUsage(candidate.products(), productUsageCounts);
            winners.add(new WinningOutfit(candidate.products(), candidate.breakdown(), hash));
        }
        return winners;
    }

    private boolean withinRepetitionCap(List<Product> products, Map<UUID, Integer> usageCounts, int maxRepetitions) {
        for (Product product : products) {
            if (usageCounts.getOrDefault(product.getId(), 0) >= maxRepetitions) {
                return false;
            }
        }
        return true;
    }

    private void recordUsage(List<Product> products, Map<UUID, Integer> usageCounts) {
        for (Product product : products) {
            usageCounts.merge(product.getId(), 1, Integer::sum);
        }
    }

    private void collectCombinationsFor(OutfitBlueprint blueprint, Set<String> genders, BigDecimal priceCeiling,
                                        List<BlueprintResult> pool) {
        List<List<Product>> candidatesBySlot = new ArrayList<>();
        for (SlotDescription slot : blueprint.slots()) {
            List<Product> candidates = fetchSlotCandidates(slot, genders, priceCeiling);
            if (candidates.isEmpty()) {
                return;
            }
            candidatesBySlot.add(candidates);
        }

        for (List<Product> combination : cartesianProduct(candidatesBySlot)) {
            if (pool.size() >= properties.generationPoolSize()) {
                return;
            }
            CompatibilityScoreBreakdown breakdown = outfitCompatibilityScorer.score(combination);
            pool.add(new BlueprintResult(combination, breakdown));
        }
    }

    private List<Product> fetchSlotCandidates(SlotDescription slot, Set<String> genders, BigDecimal priceCeiling) {
        Vector referenceEmbedding = promptQueryEmbeddingService.embed(slot.description());
        SearchResults<Product> results = productSearchService.findNearest(
                slot.role(), genders, priceCeiling, referenceEmbedding,
                ProductSearchService.UNBOUNDED_COSINE_DISTANCE, Limit.of(properties.topKPerSlot()));
        return results.getContent().stream().map(SearchResult::getContent).toList();
    }

    private List<List<Product>> cartesianProduct(List<List<Product>> candidatesBySlot) {
        List<List<Product>> combinations = new ArrayList<>();
        combinations.add(new ArrayList<>());
        for (List<Product> slotCandidates : candidatesBySlot) {
            List<List<Product>> expanded = new ArrayList<>();
            for (List<Product> partial : combinations) {
                for (Product candidate : slotCandidates) {
                    List<Product> next = new ArrayList<>(partial);
                    next.add(candidate);
                    expanded.add(next);
                }
            }
            combinations = expanded;
        }
        return combinations;
    }

    private record BlueprintResult(List<Product> products, CompatibilityScoreBreakdown breakdown) {
    }

    private record WinningOutfit(List<Product> products, CompatibilityScoreBreakdown breakdown, String itemSetHash) {
    }
}