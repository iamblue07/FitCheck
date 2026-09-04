package com.fitcheck.outfit.dto;

import java.math.BigDecimal;

public record CompatibilityScoreBreakdown(
        BigDecimal colorScore,
        BigDecimal layeringScore,
        BigDecimal structuredScore,
        BigDecimal embeddingScore,
        BigDecimal finalScore
) {
}