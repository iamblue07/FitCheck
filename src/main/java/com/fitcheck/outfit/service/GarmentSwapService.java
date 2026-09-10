package com.fitcheck.outfit.service;

import com.fitcheck.catalog.entity.Product;
import com.fitcheck.catalog.service.ProductSearchService;
import com.fitcheck.common.exception.BadRequestException;
import com.fitcheck.common.exception.ResourceNotFoundException;
import com.fitcheck.identity.entity.UserProfile;
import com.fitcheck.identity.service.UserProfileQueryService;
import com.fitcheck.outfit.properties.OutfitSwapProperties;
import com.fitcheck.outfit.dto.AlternativeCandidateResponse;
import com.fitcheck.outfit.domain.CompatibilityScoreBreakdown;
import com.fitcheck.outfit.domain.OutfitItemView;
import com.fitcheck.outfit.dto.OutfitResponse;
import com.fitcheck.outfit.entity.Outfit;
import com.fitcheck.outfit.enums.OutfitSource;
import com.fitcheck.outfit.support.BudgetCeilingResolver;
import com.fitcheck.outfit.support.OutfitGenderFilterResolver;
import com.fitcheck.outfit.support.OutfitItemSetHasher;
import lombok.AllArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@AllArgsConstructor
@EnableConfigurationProperties(OutfitSwapProperties.class)
public class GarmentSwapService {

    private final OutfitItemQueryService outfitItemQueryService;
    private final ProductSearchService productSearchService;
    private final OutfitCompatibilityScorer compatibilityScorer;
    private final OutfitGenderFilterResolver genderFilterResolver;
    private final OutfitItemSetHasher itemSetHasher;
    private final OutfitPersistenceService outfitPersistenceService;
    private final UserProfileQueryService userProfileQueryService;
    private final BudgetCeilingResolver budgetCeilingResolver;
    private final OutfitSwapProperties swapProperties;

    public List<AlternativeCandidateResponse> listAlternatives(UUID outfitId, UUID itemId, UUID userId) {
        OutfitItemQueryService.OutfitItemContext context = outfitItemQueryService.loadContext(outfitId, itemId);
        Product targetProduct = context.targetItem().getProduct();

        UserProfile profile = userProfileQueryService.getById(userId);
        Set<String> genders = genderFilterResolver.compatibleGenders(targetProduct.getGender());

        List<Product> candidates = productSearchService.findAlternatives(
                targetProduct.getArticleType(), genders, targetProduct.getId(),
                Limit.of(swapProperties.alternativeLimit()));

        BigDecimal outfitTotal = outfitItemQueryService.sumBasePrice(outfitId);
        BigDecimal ceiling = budgetCeilingResolver.resolveNullable(profile.getAverageBudgetPerOutfit());

        List<AlternativeCandidateResponse> results = new ArrayList<>();
        for (Product candidate : candidates) {
            if (budgetCeilingResolver.exceedsBudget(
                    outfitTotal, targetProduct.getBasePrice(), candidate.getBasePrice(), ceiling)) {
                continue;
            }
            List<Product> trial = new ArrayList<>(context.otherProducts());
            trial.add(candidate);
            CompatibilityScoreBreakdown projected = compatibilityScorer.score(trial);
            results.add(new AlternativeCandidateResponse(
                    candidate.getId(), candidate.getProductDisplayName(), candidate.getImageUrl(),
                    candidate.getBasePrice(), projected));
        }

        return results.stream()
                .sorted(Comparator.comparing(
                        (AlternativeCandidateResponse r) -> r.projectedBreakdown().finalScore()).reversed())
                .toList();
    }

    public OutfitResponse swap(UUID outfitId, UUID itemId, UUID productId, UUID userId) {
        OutfitItemQueryService.OutfitItemContext context = outfitItemQueryService.loadContext(outfitId, itemId);
        Product targetProduct = context.targetItem().getProduct();

        Product candidate = productSearchService.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        UserProfile profile = userProfileQueryService.getById(userId);
        Set<String> genders = genderFilterResolver.compatibleGenders(targetProduct.getGender());
        BigDecimal outfitTotal = outfitItemQueryService.sumBasePrice(outfitId);
        BigDecimal ceiling = budgetCeilingResolver.resolveNullable(profile.getAverageBudgetPerOutfit());

        validateCandidate(candidate, targetProduct, genders, outfitTotal, ceiling);

        List<Product> finalProducts = new ArrayList<>(context.otherProducts());
        finalProducts.add(candidate);

        CompatibilityScoreBreakdown breakdown = compatibilityScorer.score(finalProducts);
        String itemSetHash = itemSetHasher.hash(finalProducts);

        Outfit resultOutfit = persistOrReuse(finalProducts, breakdown, itemSetHash);

        List<OutfitItemView> items = outfitItemQueryService.findItemViews(resultOutfit.getId());
        BigDecimal totalPrice = outfitItemQueryService.sumBasePrice(resultOutfit.getId());

        return new OutfitResponse(resultOutfit.getId(), breakdown, totalPrice, items);
    }

    private void validateCandidate(Product candidate, Product targetProduct, Set<String> genders,
                                   BigDecimal outfitTotal, BigDecimal ceiling) {
        if (!candidate.getArticleType().equals(targetProduct.getArticleType())) {
            throw new BadRequestException(
                    "Product " + candidate.getId() + " is not a valid alternative for article type "
                            + targetProduct.getArticleType());
        }
        if (!genders.contains(candidate.getGender())) {
            throw new BadRequestException(
                    "Product " + candidate.getId() + " is not eligible for the requesting user's gender pool");
        }
        if (budgetCeilingResolver.exceedsBudget(outfitTotal, targetProduct.getBasePrice(), candidate.getBasePrice(), ceiling)) {
            throw new BadRequestException(
                    "Swapping in product " + candidate.getId() + " would exceed the outfit's budget ceiling");
        }
    }

    private Outfit persistOrReuse(List<Product> finalProducts, CompatibilityScoreBreakdown breakdown, String itemSetHash) {
        Optional<Outfit> existing = outfitPersistenceService.findExisting(itemSetHash);
        if (existing.isPresent()) {
            return existing.get();
        }

        try {
            return outfitPersistenceService.saveNew(finalProducts, breakdown, itemSetHash, OutfitSource.MANUAL_SWAP);
        } catch (DataIntegrityViolationException e) {
            return outfitPersistenceService.findExisting(itemSetHash)
                    .orElseThrow(() -> new IllegalStateException(
                            "Outfit insert failed on unique constraint but no existing row found for hash "
                                    + itemSetHash, e));
        }
    }
}