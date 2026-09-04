package com.fitcheck.outfit.dto;

import com.fitcheck.common.taxonomy.GarmentRole;

import java.math.BigDecimal;
import java.util.UUID;

public record OutfitItemView(
        UUID productId,
        String productDisplayName,
        String imageUrl,
        BigDecimal basePrice,
        GarmentRole slot
) {
}