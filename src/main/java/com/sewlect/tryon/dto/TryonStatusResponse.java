package com.sewlect.tryon.dto;

import com.sewlect.tryon.enums.TryonRequestStatus;

import java.util.UUID;

public record TryonStatusResponse(
        UUID id,
        TryonRequestStatus status,
        String resultImageUrl,
        String errorMessage
) {
}