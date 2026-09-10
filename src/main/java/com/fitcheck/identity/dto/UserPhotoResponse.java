package com.fitcheck.identity.dto;

import com.fitcheck.identity.enums.PhotoType;

public record UserPhotoResponse(

        PhotoType photoType,

        String downloadUrl
) {
}
