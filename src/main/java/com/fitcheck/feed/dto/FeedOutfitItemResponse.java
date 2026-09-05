package com.fitcheck.feed.dto;

import com.fitcheck.common.taxonomy.GarmentRole;

import java.math.BigDecimal;
import java.util.UUID;

public record FeedOutfitItemResponse(
        UUID itemId,
        UUID productId,
        String productDisplayName,
        String imageUrl,
        BigDecimal basePrice,
        GarmentRole slot
) {
}