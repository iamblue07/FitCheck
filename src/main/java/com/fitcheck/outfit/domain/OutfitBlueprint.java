package com.fitcheck.outfit.domain;

import java.util.List;

public record OutfitBlueprint(
        List<SlotDescription> slots
) {
}