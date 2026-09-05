package com.fitcheck.feed.service;

import com.fitcheck.catalog.service.ProductStyleTagQueryService;
import com.fitcheck.feed.config.FeedRankingProperties;
import com.fitcheck.identity.entity.UserProfile;
import com.fitcheck.identity.service.UserStylePreferenceQueryService;
import com.fitcheck.outfit.entity.Outfit;
import com.fitcheck.outfit.config.OutfitGenerationProperties;
import com.fitcheck.outfit.service.OutfitItemQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedRankingServiceTest {

    @Mock
    private UserStylePreferenceQueryService userStylePreferenceQueryService;

    @Mock
    private ProductStyleTagQueryService productStyleTagQueryService;

    @Mock
    private OutfitItemQueryService outfitItemQueryService;

    // k = 0.15, style/budget weighted 0.5/0.5 - matches the shipped defaults
    private final OutfitGenerationProperties generationProperties =
            new OutfitGenerationProperties(50, 500, new BigDecimal("0.10"), 15, 3);
    private final FeedRankingProperties rankingProperties =
            new FeedRankingProperties(new BigDecimal("0.15"), new BigDecimal("0.5"), new BigDecimal("0.5"));

    private FeedRankingService service;

    @BeforeEach
    void setUp() {
        service = new FeedRankingService(
                userStylePreferenceQueryService, productStyleTagQueryService, outfitItemQueryService,
                generationProperties, rankingProperties);
    }

    // ---------- rankScore: neutral / boundary personalization ----------

    @Test
    void rankScore_bothSignalsNeutral_equalsCompatibilityScoreExactly() {
        UserProfile profile = profileWith(null); // null budget -> neutral budgetFit
        Outfit outfit = outfitWith("0.8000");
        noStylePreferences();

        BigDecimal result = service.rankScore(profile, outfit);

        assertThat(result).isEqualByComparingTo("0.8000");
    }

    @Test
    void rankScore_perfectPersonalization_appliesUpperMultiplierBound() {
        UserProfile profile = profileWith(new BigDecimal("100")); // budget met exactly -> budgetFit 1.0
        Outfit outfit = outfitWith("0.8000");
        perfectStyleOverlap();
        stubTotalPrice(outfit, new BigDecimal("100")); // at budget

        BigDecimal result = service.rankScore(profile, outfit);

        // personalization = 1.0 -> multiplier = 1 + 0.15*2*(1.0-0.5) = 1.15
        assertThat(result).isEqualByComparingTo(new BigDecimal("0.8000").multiply(new BigDecimal("1.15")));
    }

    @Test
    void rankScore_worstPersonalization_appliesLowerMultiplierBound() {
        UserProfile profile = profileWith(new BigDecimal("100"));
        Outfit outfit = outfitWith("0.8000");
        zeroStyleOverlap();
        stubTotalPrice(outfit, new BigDecimal("1000")); // wildly over budget+tolerance -> budgetFit 0

        BigDecimal result = service.rankScore(profile, outfit);

        // personalization = 0.0 -> multiplier = 1 + 0.15*2*(0.0-0.5) = 0.85
        assertThat(result).isEqualByComparingTo(new BigDecimal("0.8000").multiply(new BigDecimal("0.85")));
    }

    // ---------- styleOverlap: Jaccard math ----------

    @Test
    void styleOverlap_emptyPreferredSet_isNeutralAndSkipsFurtherLookups() {
        UserProfile profile = profileWith(null);
        Outfit outfit = outfitWith("0.5000");
        noStylePreferences();

        service.rankScore(profile, outfit);

        // never even asks what tags the outfit has - genuine short-circuit, not just a mathematically-neutral result
        verify(outfitItemQueryService, never()).findProductIds(any());
        verify(productStyleTagQueryService, never()).findStyleTagIdsByProductIds(any());
    }

    @Test
    void styleOverlap_outfitHasNoStyleTagsButUserHasPreferences_isNeutral() {
        UserProfile profile = profileWith(null);
        Outfit outfit = outfitWith("0.5000");
        UUID preferredTag = UUID.randomUUID();
        when(userStylePreferenceQueryService.findPreferredStyleTagIds(profile.getUserId()))
                .thenReturn(Set.of(preferredTag));
        when(outfitItemQueryService.findProductIds(outfit.getId())).thenReturn(List.of(UUID.randomUUID()));
        when(productStyleTagQueryService.findStyleTagIdsByProductIds(any())).thenReturn(List.of());

        BigDecimal result = service.rankScore(profile, outfit);

        // neutral personalization overall (budget also neutral -> null) means result == compatibilityScore
        assertThat(result).isEqualByComparingTo("0.5000");
    }

    @Test
    void styleOverlap_partialOverlap_computesExactJaccardRatio() {
        UserProfile profile = profileWith(null);
        Outfit outfit = outfitWith("1.0000");
        UUID tagA = UUID.randomUUID();
        UUID tagB = UUID.randomUUID();
        UUID tagC = UUID.randomUUID();
        // preferred = {A, B}, outfit tags = {A, C} -> intersection {A}=1, union {A,B,C}=3 -> Jaccard = 1/3
        when(userStylePreferenceQueryService.findPreferredStyleTagIds(profile.getUserId()))
                .thenReturn(Set.of(tagA, tagB));
        when(outfitItemQueryService.findProductIds(outfit.getId())).thenReturn(List.of(UUID.randomUUID()));
        when(productStyleTagQueryService.findStyleTagIdsByProductIds(any())).thenReturn(List.of(tagA, tagC));

        BigDecimal result = service.rankScore(profile, outfit);

        BigDecimal jaccard = BigDecimal.ONE.divide(new BigDecimal("3"), 10, RoundingMode.HALF_UP);
        BigDecimal expectedPersonalization = jaccard.multiply(new BigDecimal("0.5"))
                .add(new BigDecimal("0.5").multiply(new BigDecimal("0.5")));
        BigDecimal expectedMultiplier = BigDecimal.ONE.add(new BigDecimal("0.15")
                .multiply(new BigDecimal("2"))
                .multiply(expectedPersonalization.subtract(new BigDecimal("0.5"))));
        assertThat(result).isEqualByComparingTo(
                new BigDecimal("1.0000").multiply(expectedMultiplier).setScale(4, RoundingMode.HALF_UP));
    }

    @Test
    void styleOverlap_identicalSets_isExactlyOne() {
        UserProfile profile = profileWith(null);
        Outfit outfit = outfitWith("1.0000");
        UUID tagA = UUID.randomUUID();
        UUID tagB = UUID.randomUUID();
        when(userStylePreferenceQueryService.findPreferredStyleTagIds(profile.getUserId()))
                .thenReturn(Set.of(tagA, tagB));
        when(outfitItemQueryService.findProductIds(outfit.getId())).thenReturn(List.of(UUID.randomUUID()));
        when(productStyleTagQueryService.findStyleTagIdsByProductIds(any())).thenReturn(List.of(tagA, tagB));

        BigDecimal result = service.rankScore(profile, outfit);

        // Jaccard = 1.0, budget neutral 0.5 -> personalization = 0.75
        // multiplier = 1 + 0.15*2*(0.75-0.5) = 1.075
        assertThat(result).isEqualByComparingTo(new BigDecimal("1.0000").multiply(new BigDecimal("1.075")));
    }

    // ---------- budgetFit: plateau / decay / clamp ----------

    @Test
    void budgetFit_nullBudget_isNeutralAndNeverQueriesPrice() {
        UserProfile profile = profileWith(null);
        Outfit outfit = outfitWith("0.6000");
        noStylePreferences();

        service.rankScore(profile, outfit);

        verify(outfitItemQueryService, never()).sumBasePrice(any());
    }

    @Test
    void budgetFit_priceExactlyAtBudget_isPlateauOne() {
        UserProfile profile = profileWith(new BigDecimal("200"));
        Outfit outfit = outfitWith("1.0000");
        noStylePreferences();
        stubTotalPrice(outfit, new BigDecimal("200"));

        BigDecimal result = service.rankScore(profile, outfit);

        assertThat(result).isEqualByComparingTo(new BigDecimal("1.0000").multiply(new BigDecimal("1.075")));
    }

    @Test
    void budgetFit_priceUnderBudget_isStillPlateauOneNotRewardedFurther() {
        UserProfile profile = profileWith(new BigDecimal("200"));
        Outfit outfit = outfitWith("1.0000");
        noStylePreferences();
        stubTotalPrice(outfit, new BigDecimal("50")); // well under budget

        BigDecimal result = service.rankScore(profile, outfit);

        // same as exactly-at-budget - cheaper is NOT rewarded beyond the plateau
        assertThat(result).isEqualByComparingTo(new BigDecimal("1.0000").multiply(new BigDecimal("1.075")));
    }

    @Test
    void budgetFit_priceExactlyAtToleranceEdge_isClampedToZero() {
        UserProfile profile = profileWith(new BigDecimal("100")); // tolerance 0.10 -> edge at 110
        Outfit outfit = outfitWith("1.0000");
        noStylePreferences();
        stubTotalPrice(outfit, new BigDecimal("110"));

        BigDecimal result = service.rankScore(profile, outfit);

        assertThat(result).isEqualByComparingTo(new BigDecimal("1.0000").multiply(new BigDecimal("0.925")));
    }

    @Test
    void budgetFit_priceWildlyBeyondToleranceEdge_staysClampedToZeroNotNegative() {
        UserProfile profile = profileWith(new BigDecimal("100"));
        Outfit outfit = outfitWith("1.0000");
        noStylePreferences();
        stubTotalPrice(outfit, new BigDecimal("999999")); // this outfit was cached for a much richer user

        BigDecimal result = service.rankScore(profile, outfit);

        assertThat(result).isEqualByComparingTo(new BigDecimal("1.0000").multiply(new BigDecimal("0.925")));
    }

    @Test
    void budgetFit_priceHalfwayThroughToleranceBand_decaysLinearlyToHalf() {
        UserProfile profile = profileWith(new BigDecimal("100")); // tolerance edge at 110, halfway = 105
        Outfit outfit = outfitWith("1.0000");
        noStylePreferences();
        stubTotalPrice(outfit, new BigDecimal("105"));

        BigDecimal result = service.rankScore(profile, outfit);

        // budgetFit = 1 - (105-100)/(100*0.10) = 0.5 (exactly neutral by coincidence)
        // personalization = 0.5*0.5 + 0.5*0.5 = 0.5 -> multiplier 1.0 -> result == compatibilityScore
        assertThat(result).isEqualByComparingTo("1.0000");
    }

    @Test
    void budgetFit_zeroBudgetWithAnyRealPrice_isImmediatelyClampedToZero_noDivisionByZero() {
        UserProfile profile = profileWith(BigDecimal.ZERO);
        Outfit outfit = outfitWith("1.0000");
        noStylePreferences();
        stubTotalPrice(outfit, new BigDecimal("10")); // any positive price already exceeds a 0 budget's tolerance edge (also 0)

        BigDecimal result = service.rankScore(profile, outfit);

        assertThat(result).isEqualByComparingTo(new BigDecimal("1.0000").multiply(new BigDecimal("0.925")));
    }

    @Test
    void budgetFit_zeroBudgetAndZeroPrice_isPlateauOneNotDivisionByZero() {
        UserProfile profile = profileWith(BigDecimal.ZERO);
        Outfit outfit = outfitWith("1.0000");
        noStylePreferences();
        stubTotalPrice(outfit, BigDecimal.ZERO); // free outfit exactly matches a zero budget

        BigDecimal result = service.rankScore(profile, outfit);

        assertThat(result).isEqualByComparingTo(new BigDecimal("1.0000").multiply(new BigDecimal("1.075")));
    }

    // ---------- totalPrice ----------

    @Test
    void totalPrice_delegatesDirectlyToOutfitItemQueryService() {
        Outfit outfit = outfitWith("1.0000");
        when(outfitItemQueryService.sumBasePrice(outfit.getId())).thenReturn(new BigDecimal("123.45"));

        BigDecimal result = service.totalPrice(outfit);

        assertThat(result).isEqualByComparingTo("123.45");
        verify(outfitItemQueryService).sumBasePrice(outfit.getId());
    }

    // ---------- rounding ----------

    @Test
    void rankScore_result_isAlwaysScaledToFourDecimalPlaces() {
        UserProfile profile = profileWith(null);
        Outfit outfit = outfitWith("0.3333");
        noStylePreferences();

        BigDecimal result = service.rankScore(profile, outfit);

        assertThat(result.scale()).isEqualTo(4);
    }

    // ---------- fixtures ----------

    private UserProfile profileWith(BigDecimal budget) {
        return UserProfile.builder().userId(UUID.randomUUID()).averageBudgetPerOutfit(budget).build();
    }

    private Outfit outfitWith(String compatibilityScore) {
        return Outfit.builder().id(UUID.randomUUID()).compatibilityScore(new BigDecimal(compatibilityScore)).build();
    }

    private void noStylePreferences() {
        when(userStylePreferenceQueryService.findPreferredStyleTagIds(any())).thenReturn(Set.of());
    }

    private void perfectStyleOverlap() {
        UUID tag = UUID.randomUUID();
        when(userStylePreferenceQueryService.findPreferredStyleTagIds(any())).thenReturn(Set.of(tag));
        when(outfitItemQueryService.findProductIds(any())).thenReturn(List.of(UUID.randomUUID()));
        when(productStyleTagQueryService.findStyleTagIdsByProductIds(any())).thenReturn(List.of(tag));
    }

    private void zeroStyleOverlap() {
        when(userStylePreferenceQueryService.findPreferredStyleTagIds(any())).thenReturn(Set.of(UUID.randomUUID()));
        when(outfitItemQueryService.findProductIds(any())).thenReturn(List.of(UUID.randomUUID()));
        when(productStyleTagQueryService.findStyleTagIdsByProductIds(any())).thenReturn(List.of(UUID.randomUUID()));
    }

    private void stubTotalPrice(Outfit outfit, BigDecimal price) {
        when(outfitItemQueryService.sumBasePrice(eq(outfit.getId()))).thenReturn(price);
    }
}