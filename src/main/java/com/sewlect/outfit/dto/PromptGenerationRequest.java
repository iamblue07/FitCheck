package com.sewlect.outfit.dto;

import jakarta.validation.constraints.NotBlank;

public record PromptGenerationRequest(
        @NotBlank
        String prompt,
        Boolean matchProfile
) {
}