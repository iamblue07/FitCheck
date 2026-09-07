package com.fitcheck.outfit.dto;

import java.util.List;

public record OutfitBlueprint(
        List<SlotDescription> slots
) {
}