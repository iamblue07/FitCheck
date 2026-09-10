package com.fitcheck.outfit.domain;

import com.fitcheck.common.taxonomy.enums.GarmentRole;

public record SlotDescription(
        GarmentRole role,
        String description
) {
}