package com.sewlect.identity.dto;

import java.time.LocalDateTime;

public record PresignedUploadResponse(

        String uploadUrl,

        LocalDateTime expiresAt
) {
}
