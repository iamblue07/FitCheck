package com.fitcheck.outfit.dto;

import java.util.List;

public record StructuredPromptQuery(
        List<OutfitBlueprint> blueprints
) {
}