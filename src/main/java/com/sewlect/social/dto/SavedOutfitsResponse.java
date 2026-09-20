package com.sewlect.social.dto;

import com.sewlect.outfit.dto.OutfitResponse;

import java.util.List;

public record SavedOutfitsResponse(
        List<OutfitResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}