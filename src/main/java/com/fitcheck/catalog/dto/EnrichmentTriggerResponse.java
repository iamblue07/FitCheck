package com.fitcheck.catalog.dto;

import java.util.UUID;

public record EnrichmentTriggerResponse(
        boolean enriched,
        UUID productId,
        String productDisplayName
) {
}
