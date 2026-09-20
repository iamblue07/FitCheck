package com.sewlect.identity.dto;

import com.sewlect.identity.enums.PhotoType;

public record UserPhotoResponse(

        PhotoType photoType,

        String downloadUrl
) {
}
