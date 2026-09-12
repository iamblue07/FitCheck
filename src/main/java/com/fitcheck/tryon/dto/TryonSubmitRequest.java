package com.fitcheck.tryon.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TryonSubmitRequest(
        @NotNull
        UUID outfitId
) {
}