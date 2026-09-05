package com.fitcheck.outfit.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SwapRequest(
        @NotNull
        UUID productId
) {
}