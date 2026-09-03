package com.fitcheck.outfit.service;

import java.math.BigDecimal;

public record CompatibilityScoreBreakdown(
        BigDecimal colorScore,
        BigDecimal layeringScore,
        BigDecimal structuredScore,
        BigDecimal embeddingScore,
        BigDecimal finalScore
) {
}