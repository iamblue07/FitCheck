package com.sewlect.outfit.service;

import com.sewlect.catalog.entity.Product;
import com.sewlect.catalog.service.ProductSearchService;
import com.sewlect.common.exception.BadRequestException;
import com.sewlect.common.exception.ResourceNotFoundException;
import com.sewlect.identity.entity.UserProfile;
import com.sewlect.identity.service.UserProfileQueryService;
import com.sewlect.outfit.properties.OutfitSwapProperties;
import com.sewlect.outfit.dto.AlternativeCandidateResponse;
import com.sewlect.outfit.domain.CompatibilityScoreBreakdown;
import com.sewlect.outfit.domain.OutfitItemView;
import com.sewlect.outfit.dto.OutfitResponse;
import com.sewlect.outfit.entity.Outfit;
import com.sewlect.outfit.enums.OutfitSource;
import com.sewlect.outfit.support.BudgetCeilingResolver;
import com.sewlect.outfit.support.OutfitGenderFilterResolver;
import com.sewlect.outfit.support.OutfitItemSetHasher;
import lombok.AllArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

        Outfit resultOutfit = outfitPersistenceService.saveOrReuse(
                finalProducts, breakdown, itemSetHash, OutfitSource.MANUAL_SWAP);

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
}