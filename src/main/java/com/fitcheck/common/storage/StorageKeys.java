package com.fitcheck.common.storage;

import com.fitcheck.common.exception.InvalidPhotoTypeException;

import java.util.Set;
import java.util.UUID;

public final class StorageKeys {

    private static final Set<String> VALID_PHOTO_TYPES = Set.of("front", "back");

    private StorageKeys() {
    }

    public static String bodyPhotoKey(UUID userId, String photoType) {
        if (!VALID_PHOTO_TYPES.contains(photoType)) {
            throw new InvalidPhotoTypeException(
                    "Invalid photo type: '" + photoType + "'. Expected 'front' or 'back'.");
        }
        return String.format("body-photos/%s/%s.jpg", userId, photoType);
    }

    public static String tryonResultKey(UUID requestId) {
        return String.format("tryon-results/%s.jpg", requestId);
    }
}