package com.sewlect.identity.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(

        @NotBlank
        String refreshToken
) {
}
