package com.fitcheck.outfit.service;

import com.fitcheck.catalog.entity.Product;
import com.fitcheck.catalog.service.ProductSearchService;
import com.fitcheck.identity.entity.UserProfile;
import com.fitcheck.identity.service.UserProfileQueryService;
import com.fitcheck.outfit.config.OutfitPromptProperties;
import com.fitcheck.outfit.dto.AlternativeCandidateResponse;
import com.fitcheck.outfit.dto.CompatibilityScoreBreakdown;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@AllArgsConstructor
@EnableConfigurationProperties(OutfitPromptProperties.class)
public class PromptRefinementService {

    private final PromptExtractionService promptExtractionService;
    private final PromptQueryEmbeddingService promptQueryEmbeddingService;
    private final ProductSearchService productSearchService;
    private final OutfitCompatibilityScorer outfitCompatibilityScorer;
    private final OutfitGenderFilterResolver outfitGenderFilterResolver;
    private final OutfitItemQueryService outfitItemQueryService;
    private final UserProfileQueryService userProfileQueryService;
    private final BudgetCeilingResolver budgetCeilingResolver;
    private final OutfitPromptProperties properties;

    public List<AlternativeCandidateResponse> refine(UUID outfitId, UUID itemId, UUID userId, String rawPrompt) {
        OutfitItemQueryService.OutfitItemContext context = outfitItemQueryService.loadContext(outfitId, itemId);
        Product targetProduct = context.targetItem().getProduct();

        String slotDescription = promptExtractionService.extractSingleSlot(rawPrompt, targetProduct.getGarmentRole());

        UserProfile profile = userProfileQueryService.getById(userId);
        Set<String> genders = outfitGenderFilterResolver.allowedGenders(profile.getSex());
        BigDecimal outfitTotal = outfitItemQueryService.sumBasePrice(outfitId);
        BigDecimal queryPriceCeiling = budgetCeilingResolver.resolve(profile.getAverageBudgetPerOutfit());
        BigDecimal budgetCeiling = budgetCeilingResolver.resolveNullable(profile.getAverageBudgetPerOutfit());

        Vector referenceEmbedding = promptQueryEmbeddingService.embed(slotDescription);
        SearchResults<Product> results = productSearchService.findNearest(
                targetProduct.getGarmentRole(), genders, queryPriceCeiling, referenceEmbedding,
                ProductSearchService.UNBOUNDED_COSINE_DISTANCE, Limit.of(properties.topKPerSlot()));

        List<AlternativeCandidateResponse> candidates = new ArrayList<>();
        for (SearchResult<Product> result : results.getContent()) {
            Product candidate = result.getContent();
            if (candidate.getId().equals(targetProduct.getId())) {
                continue;
            }
            if (budgetCeilingResolver.exceedsBudget(
                    outfitTotal, targetProduct.getBasePrice(), candidate.getBasePrice(), budgetCeiling)) {
                continue;
            }
            List<Product> trial = new ArrayList<>(context.otherProducts());
            trial.add(candidate);
            CompatibilityScoreBreakdown projected = outfitCompatibilityScorer.score(trial);
            candidates.add(new AlternativeCandidateResponse(
                    candidate.getId(), candidate.getProductDisplayName(), candidate.getImageUrl(),
                    candidate.getBasePrice(), projected));
        }

        return candidates.stream()
                .sorted(Comparator.comparing(
                        (AlternativeCandidateResponse r) -> r.projectedBreakdown().finalScore()).reversed())
                .toList();
    }
}