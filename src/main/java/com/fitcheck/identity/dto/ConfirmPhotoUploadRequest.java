package com.fitcheck.identity.dto;

import com.fitcheck.identity.enums.PhotoType;
import jakarta.validation.constraints.NotNull;

public record ConfirmPhotoUploadRequest(

        @NotNull
        PhotoType photoType
) {
}
