package com.fitcheck.identity.dto;

import java.time.LocalDateTime;

public record PresignedUploadResponse(

        String uploadUrl,

        LocalDateTime expiresAt
) {
}
