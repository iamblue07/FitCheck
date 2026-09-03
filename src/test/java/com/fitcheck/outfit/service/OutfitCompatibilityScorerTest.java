package com.fitcheck.outfit.service;

import com.fitcheck.catalog.entity.Product;
import com.fitcheck.common.taxonomy.GarmentRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutfitCompatibilityScorerTest {

    private OutfitCompatibilityScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = scorerWithWeights("0.5", "0.5");
    }

    @Test
    void score_fewerThanTwoProducts_throwsIllegalArgumentException() {
        Product single = productWith("black", GarmentRole.TOP, "base", unitVector());

        assertThatThrownBy(() -> scorer.score(List.of(single)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void score_emptyList_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> scorer.score(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void score_sameColorFamily_scoresFullMatch() {
        Product a = productWith("navy", GarmentRole.TOP, "base", unitVector());
        Product b = productWith("black", GarmentRole.BOTTOM, "base", unitVector());

        assertThat(scorer.score(List.of(a, b)).colorScore()).isEqualByComparingTo("1.0");
    }

    @Test
    void score_oneNeutralOneNonNeutral_scoresNeutralBonus() {
        Product a = productWith("black", GarmentRole.TOP, "base", unitVector());
        Product b = productWith("red", GarmentRole.BOTTOM, "base", unitVector());

        assertThat(scorer.score(List.of(a, b)).colorScore()).isEqualByComparingTo("0.9");
    }

    @Test
    void score_differentNonNeutralFamilies_scoresOtherwise() {
        Product a = productWith("red", GarmentRole.TOP, "base", unitVector());
        Product b = productWith("blue", GarmentRole.BOTTOM, "base", unitVector());

        assertThat(scorer.score(List.of(a, b)).colorScore()).isEqualByComparingTo("0.5");
    }

    @Test
    void score_unmappedIdenticalColorStrings_treatedAsSameFamily() {
        Product a = productWith("chartreuse", GarmentRole.TOP, "base", unitVector());
        Product b = productWith("Chartreuse", GarmentRole.BOTTOM, "base", unitVector());

        assertThat(scorer.score(List.of(a, b)).colorScore()).isEqualByComparingTo("1.0");
    }

    @Test
    void score_unmappedDifferentColorStrings_scoresOtherwise() {
        Product a = productWith("chartreuse", GarmentRole.TOP, "base", unitVector());
        Product b = productWith("periwinkle", GarmentRole.BOTTOM, "base", unitVector());

        assertThat(scorer.score(List.of(a, b)).colorScore()).isEqualByComparingTo("0.5");
    }

    @Test
    void score_twoLayeringRoleOuterItemsAmongTopAndOuterwear_scoresConflict() {
        Product top = productWith("black", GarmentRole.TOP, "outer", unitVector());
        Product outerwear = productWith("black", GarmentRole.OUTERWEAR, "outer", unitVector());

        assertThat(scorer.score(List.of(top, outerwear)).layeringScore()).isEqualByComparingTo("0.4");
    }

    @Test
    void score_atMostOneOuterLayeringItem_scoresNoConflict() {
        Product top = productWith("black", GarmentRole.TOP, "outer", unitVector());
        Product outerwear = productWith("black", GarmentRole.OUTERWEAR, "base", unitVector());

        assertThat(scorer.score(List.of(top, outerwear)).layeringScore()).isEqualByComparingTo("1.0");
    }

    @Test
    void score_layeringRoleOuterOnAccessory_isIgnoredSinceOnlyTopAndOuterwearAreScoped() {
        Product top = productWith("black", GarmentRole.TOP, "outer", unitVector());
        Product footwear = productWith("black", GarmentRole.FOOTWEAR, null, unitVector());
        Product accessory = productWith("black", GarmentRole.ACCESSORY, "outer", unitVector());

        BigDecimal layeringScore = scorer.score(List.of(top, footwear, accessory)).layeringScore();

        assertThat(layeringScore).isEqualByComparingTo("1.0");
    }

    @Test
    void score_embeddingComponent_isRescaledCosineSimilarityAveragedAcrossPairs() {
        Product a = productWith("black", GarmentRole.TOP, "base", new float[]{1f, 0f, 0f});
        Product b = productWith("black", GarmentRole.BOTTOM, "base", new float[]{0f, 1f, 0f}); // orthogonal

        BigDecimal embeddingScore = scorer.score(List.of(a, b)).embeddingScore();

        assertThat(embeddingScore).isEqualByComparingTo("0.5");
    }

    @Test
    void score_identicalEmbeddings_scoreMaximumSimilarity() {
        Product a = productWith("black", GarmentRole.TOP, "base", new float[]{1f, 0f, 0f});
        Product b = productWith("black", GarmentRole.BOTTOM, "base", new float[]{1f, 0f, 0f});

        assertThat(scorer.score(List.of(a, b)).embeddingScore()).isEqualByComparingTo("1.0");
    }

    @Test
    void score_finalScore_isWeightedCombinationRoundedToFourDecimals() {
        Product a = productWith("black", GarmentRole.TOP, "base", new float[]{1f, 0f, 0f});
        Product b = productWith("white", GarmentRole.BOTTOM, "base", new float[]{1f, 0f, 0f});

        BigDecimal finalScore = scorer.score(List.of(a, b)).finalScore();

        // color: both NEUTRAL -> 1.0; layering: no TOP/OUTERWEAR conflict -> 1.0; structured avg -> 1.0
        // embedding: identical vectors -> 1.0; final = 0.5*1.0 + 0.5*1.0 = 1.0
        assertThat(finalScore).isEqualByComparingTo("1.0000");
        assertThat(finalScore.scale()).isEqualTo(4);
    }

    @Test
    void score_weightsSumAboveOne_finalScoreClampedToOne() {
        OutfitCompatibilityScorer overweighted = scorerWithWeights("1.0", "1.0");
        Product a = productWith("black", GarmentRole.TOP, "base", new float[]{1f, 0f, 0f});
        Product b = productWith("white", GarmentRole.BOTTOM, "base", new float[]{1f, 0f, 0f});

        BigDecimal finalScore = overweighted.score(List.of(a, b)).finalScore();

        // unclamped this would be 2.0000 (1.0*1.0 + 1.0*1.0) - proves the clamp actually engages
        assertThat(finalScore).isEqualByComparingTo("1.0000");
    }

    private OutfitCompatibilityScorer scorerWithWeights(String structuredWeight, String embeddingWeight) {
        return new OutfitCompatibilityScorer(
                new OutfitCompatibilityProperties(new BigDecimal(structuredWeight), new BigDecimal(embeddingWeight)));
    }

    private Product productWith(String primaryColor, GarmentRole role, String layeringRole, float[] embedding) {
        return Product.builder()
                .id(UUID.randomUUID())
                .primaryColor(primaryColor)
                .garmentRole(role)
                .layeringRole(layeringRole)
                .textEmbedding(embedding)
                .build();
    }

    private float[] unitVector() {
        return new float[]{1f, 0f, 0f};
    }
}