package com.sewlect.identity.dto;

import com.sewlect.identity.enums.PhotoType;
import jakarta.validation.constraints.NotNull;

public record ConfirmPhotoUploadRequest(

        @NotNull
        PhotoType photoType
) {
}
