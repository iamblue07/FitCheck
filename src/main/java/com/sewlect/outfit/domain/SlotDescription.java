package com.sewlect.outfit.domain;

import com.sewlect.common.taxonomy.enums.GarmentRole;

public record SlotDescription(
        GarmentRole role,
        String description
) {
}