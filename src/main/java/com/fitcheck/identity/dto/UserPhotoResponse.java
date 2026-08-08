package com.fitcheck.identity.dto;

import com.fitcheck.identity.entity.PhotoType;

public record UserPhotoResponse(

        PhotoType photoType,

        String downloadUrl
) {
}
