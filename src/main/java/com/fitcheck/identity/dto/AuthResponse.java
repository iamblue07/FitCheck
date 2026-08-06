package com.fitcheck.identity.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {
    public AuthResponse {
        tokenType = "Bearer";
    }

    public static AuthResponse of(String accessToken, String refreshToken, long expiresIn) {
        return new AuthResponse(accessToken, refreshToken, null, expiresIn);
    }
}
