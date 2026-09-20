package com.sewlect.outfit.domain;

import java.util.List;

public record OutfitBlueprint(
        List<SlotDescription> slots
) {
}