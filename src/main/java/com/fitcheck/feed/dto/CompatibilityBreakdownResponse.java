package com.fitcheck.feed.dto;

import java.math.BigDecimal;

public record CompatibilityBreakdownResponse(
        BigDecimal colorScore,
        BigDecimal layeringScore,
        BigDecimal structuredScore,
        BigDecimal embeddingScore,
        BigDecimal finalScore
) {
}