package com.fitcheck.feed.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record FeedItemResponse(
        UUID outfitId,
        CompatibilityBreakdownResponse compatibilityBreakdown,
        BigDecimal rankScore,
        BigDecimal totalPrice,
        List<FeedOutfitItemResponse> items
) {
}