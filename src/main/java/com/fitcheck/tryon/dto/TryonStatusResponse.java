package com.fitcheck.tryon.dto;

import com.fitcheck.tryon.enums.TryonRequestStatus;

import java.util.UUID;

public record TryonStatusResponse(
        UUID id,
        TryonRequestStatus status,
        String resultImageUrl,
        String errorMessage
) {
}