package com.fitcheck.outfit.dto;

import jakarta.validation.constraints.NotBlank;

public record PromptRefinementRequest(
        @NotBlank
        String prompt
) {
}