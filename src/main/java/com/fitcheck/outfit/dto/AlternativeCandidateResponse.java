package com.fitcheck.outfit.dto;

import com.fitcheck.outfit.domain.CompatibilityScoreBreakdown;

import java.math.BigDecimal;
import java.util.UUID;

public record AlternativeCandidateResponse(
        UUID productId,
        String productDisplayName,
        String imageUrl,
        BigDecimal basePrice,
        CompatibilityScoreBreakdown projectedBreakdown
) {
}