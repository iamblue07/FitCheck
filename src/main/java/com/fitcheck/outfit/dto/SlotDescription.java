package com.fitcheck.outfit.dto;

import com.fitcheck.common.taxonomy.GarmentRole;

public record SlotDescription(
        GarmentRole role,
        String description
) {
}