package com.fitcheck.outfit.service;

import com.fitcheck.catalog.entity.Product;
import com.fitcheck.common.taxonomy.GarmentRole;
import lombok.AllArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

@Service
@AllArgsConstructor
@EnableConfigurationProperties(OutfitCompatibilityProperties.class)
public class OutfitCompatibilityScorer {

    private static final int INTERNAL_SCALE = 10;

    private static final BigDecimal COLOR_SAME_FAMILY_SCORE = BigDecimal.ONE;
    private static final BigDecimal COLOR_EITHER_NEUTRAL_SCORE = new BigDecimal("0.9");
    private static final BigDecimal COLOR_OTHERWISE_SCORE = new BigDecimal("0.5");
    private static final BigDecimal LAYERING_OK_SCORE = BigDecimal.ONE;
    private static final BigDecimal LAYERING_CONFLICT_SCORE = new BigDecimal("0.4");

    private final OutfitCompatibilityProperties properties;

    public CompatibilityScoreBreakdown score(List<Product> products) {
        if (products.size() < 2) {
            throw new IllegalArgumentException(
                    "OutfitCompatibilityScorer requires at least 2 products, got " + products.size());
        }

        BigDecimal colorScore = average(pairwiseScores(products, this::colorPairScore));
        BigDecimal layeringScore = layeringScore(products);
        BigDecimal structuredScore = average(List.of(colorScore, layeringScore));
        BigDecimal embeddingScore = average(pairwiseScores(products, this::embeddingPairScore));

        BigDecimal rawFinal = properties.structuredWeight().multiply(structuredScore)
                .add(properties.embeddingWeight().multiply(embeddingScore));
        BigDecimal finalScore = rawFinal.max(BigDecimal.ZERO).min(BigDecimal.ONE)
                .setScale(4, RoundingMode.HALF_UP);

        return new CompatibilityScoreBreakdown(colorScore, layeringScore, structuredScore, embeddingScore, finalScore);
    }

    private List<BigDecimal> pairwiseScores(List<Product> products, BiFunction<Product, Product, BigDecimal> scorer) {
        List<BigDecimal> scores = new ArrayList<>();
        for (int i = 0; i < products.size(); i++) {
            for (int j = i + 1; j < products.size(); j++) {
                scores.add(scorer.apply(products.get(i), products.get(j)));
            }
        }
        return scores;
    }

    private BigDecimal average(List<BigDecimal> values) {
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), INTERNAL_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal colorPairScore(Product a, Product b) {
        ColorClassification classificationA = ColorFamily.classify(a.getPrimaryColor());
        ColorClassification classificationB = ColorFamily.classify(b.getPrimaryColor());

        if (classificationA.sameFamilyAs(classificationB)) {
            return COLOR_SAME_FAMILY_SCORE;
        }
        if (classificationA.isNeutral() || classificationB.isNeutral()) {
            return COLOR_EITHER_NEUTRAL_SCORE;
        }
        return COLOR_OTHERWISE_SCORE;
    }

    private BigDecimal layeringScore(List<Product> products) {
        long outerCount = products.stream()
                .filter(p -> p.getGarmentRole() == GarmentRole.TOP || p.getGarmentRole() == GarmentRole.OUTERWEAR)
                .map(Product::getLayeringRole)
                .filter(role -> role != null && "outer".equalsIgnoreCase(role.trim()))
                .count();
        return outerCount <= 1 ? LAYERING_OK_SCORE : LAYERING_CONFLICT_SCORE;
    }

    private BigDecimal embeddingPairScore(Product a, Product b) {
        double similarity = dotProduct(a.getTextEmbedding(), b.getTextEmbedding());
        return BigDecimal.valueOf((similarity + 1.0) / 2.0);
    }

    private double dotProduct(float[] a, float[] b) {
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            sum += (double) a[i] * b[i];
        }
        return sum;
    }

    private enum ColorFamily {
        NEUTRAL("black", "white", "grey", "gray", "navy", "beige", "cream", "ivory", "tan", "brown", "khaki", "denim",
                "charcoal", "silver", "gold", "taupe", "bronze", "copper", "clear", "nude", "camel", "gunmetal", "caramel"),
        RED("red", "maroon", "burgundy", "crimson", "wine"),
        ORANGE("orange", "rust", "coral", "peach", "apricot", "amber", "terracotta"),
        YELLOW("yellow", "mustard", "lemon"),
        GREEN("green", "olive", "sage", "mint", "emerald"),
        BLUE("blue", "teal", "turquoise", "indigo", "cobalt"),
        PURPLE("purple", "lavender", "violet", "plum", "mauve", "mulberry"),
        PINK("pink", "rose", "blush", "fuchsia", "magenta", "berry", "raspberry");

        private final Set<String> keywords;

        ColorFamily(String... keywords) {
            this.keywords = Set.of(keywords);
        }

        private boolean matches(List<String> tokens) {
            return tokens.stream().anyMatch(keywords::contains);
        }

        static ColorClassification classify(String primaryColor) {
            String normalized = primaryColor.strip().toLowerCase();
            List<String> tokens = List.of(normalized.split("[\\s-]+"));
            for (ColorFamily family : values()) {
                if (family.matches(tokens)) {
                    return new ColorClassification(family, normalized);
                }
            }
            return new ColorClassification(null, normalized);
        }
    }

    private record ColorClassification(ColorFamily family, String normalizedColor) {
        boolean sameFamilyAs(ColorClassification other) {
            if (family != null && family == other.family) {
                return true;
            }
            return family == null && other.family == null && normalizedColor.equals(other.normalizedColor);
        }

        boolean isNeutral() {
            return family == ColorFamily.NEUTRAL;
        }
    }
}