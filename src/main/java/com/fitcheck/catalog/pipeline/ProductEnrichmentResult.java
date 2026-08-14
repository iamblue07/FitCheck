package com.fitcheck.catalog.pipeline;

import java.math.BigDecimal;
import java.util.List;

public record ProductEnrichmentResult(
        String fit,
        String silhouette,
        String pattern,
        String materialGuess,
        String formality,
        String description,
        String occasion,
        String primaryColor,
        String secondaryColor,
        String layeringRole,
        List<String> styleTagNames,
        BigDecimal basePrice
) {
}
