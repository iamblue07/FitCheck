package com.fitcheck.outfit.domain;

import java.util.List;

public record StructuredPromptQuery(
        List<OutfitBlueprint> blueprints
) {
}