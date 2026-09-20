package com.sewlect.outfit.domain;

import java.util.List;

public record StructuredPromptQuery(
        List<OutfitBlueprint> blueprints
) {
}