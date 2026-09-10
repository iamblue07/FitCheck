package com.fitcheck.outfit.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public record PromptInferredQuery(
        List<OutfitBlueprint> blueprints,
        Set<String> genders,
        BigDecimal budget
) {
}