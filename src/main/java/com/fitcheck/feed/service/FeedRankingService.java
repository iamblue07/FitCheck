package com.fitcheck.feed.service;

import com.fitcheck.catalog.service.ProductStyleTagQueryService;
import com.fitcheck.feed.config.FeedRankingProperties;
import com.fitcheck.identity.entity.UserProfile;
import com.fitcheck.outfit.entity.Outfit;
import com.fitcheck.outfit.config.OutfitGenerationProperties;
import com.fitcheck.outfit.service.OutfitItemQueryService;
import lombok.AllArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@AllArgsConstructor
@EnableConfigurationProperties(FeedRankingProperties.class)
public class FeedRankingService {

    private static final int INTERNAL_SCALE = 10;
    private static final BigDecimal NEUTRAL_SCORE = new BigDecimal("0.5");
    private static final BigDecimal TWO = new BigDecimal("2");

    private final ProductStyleTagQueryService productStyleTagQueryService;
    private final OutfitItemQueryService outfitItemQueryService;
    private final OutfitGenerationProperties generationProperties;
    private final FeedRankingProperties rankingProperties;

    public BigDecimal rankScore(UserProfile profile, Outfit outfit, Set<UUID> preferredStyleTagIds) {
        BigDecimal styleOverlap = styleOverlap(preferredStyleTagIds, outfit);
        BigDecimal budgetFit = budgetFit(profile, outfit);

        BigDecimal personalization = rankingProperties.styleWeight().multiply(styleOverlap)
                .add(rankingProperties.budgetWeight().multiply(budgetFit));

        BigDecimal multiplier = BigDecimal.ONE.add(
                rankingProperties.personalizationWeight()
                        .multiply(TWO)
                        .multiply(personalization.subtract(NEUTRAL_SCORE)));

        return outfit.getCompatibilityScore().multiply(multiplier).setScale(4, RoundingMode.HALF_UP);
    }

    public BigDecimal totalPrice(Outfit outfit) {
        return outfitItemQueryService.sumBasePrice(outfit.getId());
    }

    private BigDecimal styleOverlap(Set<UUID> preferredStyleTagIds, Outfit outfit) {
        if (preferredStyleTagIds.isEmpty()) {
            return NEUTRAL_SCORE;
        }

        List<UUID> outfitProductIds = outfitItemQueryService.findProductIds(outfit.getId());
        Set<UUID> outfitTagIds = Set.copyOf(productStyleTagQueryService.findStyleTagIdsByProductIds(outfitProductIds));
        if (outfitTagIds.isEmpty()) {
            return NEUTRAL_SCORE;
        }

        Set<UUID> intersection = new HashSet<>(preferredStyleTagIds);
        intersection.retainAll(outfitTagIds);

        Set<UUID> union = new HashSet<>(preferredStyleTagIds);
        union.addAll(outfitTagIds);

        return BigDecimal.valueOf(intersection.size())
                .divide(BigDecimal.valueOf(union.size()), INTERNAL_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal budgetFit(UserProfile profile, Outfit outfit) {
        BigDecimal budget = profile.getAverageBudgetPerOutfit();
        if (budget == null) {
            return NEUTRAL_SCORE;
        }

        BigDecimal totalPrice = totalPrice(outfit);
        if (totalPrice.compareTo(budget) <= 0) {
            return BigDecimal.ONE;
        }

        BigDecimal toleranceAmount = budget.multiply(generationProperties.budgetTolerance());
        BigDecimal toleranceEdge = budget.add(toleranceAmount);
        if (totalPrice.compareTo(toleranceEdge) >= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal overage = totalPrice.subtract(budget);
        return BigDecimal.ONE.subtract(overage.divide(toleranceAmount, INTERNAL_SCALE, RoundingMode.HALF_UP));
    }
}