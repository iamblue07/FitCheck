package com.fitcheck.outfit.dto;

import com.fitcheck.outfit.domain.CompatibilityScoreBreakdown;
import com.fitcheck.outfit.domain.OutfitItemView;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OutfitResponse(
        UUID outfitId,
        CompatibilityScoreBreakdown compatibilityBreakdown,
        BigDecimal totalPrice,
        List<OutfitItemView> items
) {
}