package com.fitcheck.identity.service;

import com.fitcheck.common.exception.BadRequestException;
import com.fitcheck.common.storage.StorageKeys;
import com.fitcheck.common.storage.StorageService;
import com.fitcheck.identity.dto.PresignedUploadResponse;
import com.fitcheck.identity.dto.UserPhotoResponse;
import com.fitcheck.identity.entity.PhotoType;
import com.fitcheck.identity.entity.UserBodyPhoto;
import com.fitcheck.identity.repository.UserBodyPhotoRepository;
import com.fitcheck.identity.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class PhotoService {

    private static final String BODY_PHOTO_CONTENT_TYPE = "image/jpeg";

    private final UserBodyPhotoRepository userBodyPhotoRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;

    public PresignedUploadResponse generateUploadUrl(UUID userId, PhotoType photoType) {
        String key = StorageKeys.bodyPhotoKey(userId, photoType.name().toLowerCase());

        URI uploadUrl = storageService.generateUploadUrl(key, BODY_PHOTO_CONTENT_TYPE, StorageService.DEFAULT_TTL);
        LocalDateTime expiresAt = LocalDateTime.now().plus(StorageService.DEFAULT_TTL);

        return new PresignedUploadResponse(uploadUrl.toString(), expiresAt);
    }

    @Transactional
    public UserPhotoResponse confirmUpload(UUID userId, PhotoType photoType) {
        String key = StorageKeys.bodyPhotoKey(userId, photoType.name().toLowerCase());

        if (!storageService.exists(key)) {
            throw new BadRequestException("No upload found for photo type: " + photoType);
        }

        UserBodyPhoto photo = userBodyPhotoRepository.findByUserIdAndPhotoType(userId, photoType)
                .orElseGet(() -> newPhoto(userId, photoType));
        photo.setStorageKey(key);
        userBodyPhotoRepository.save(photo);

        return toPhotoResponse(photo);
    }

    @Transactional(readOnly = true)
    public List<UserPhotoResponse> listPhotos(UUID userId) {
        return userBodyPhotoRepository.findAllByUserId(userId).stream()
                .map(this::toPhotoResponse)
                .toList();
    }

    private UserBodyPhoto newPhoto(UUID userId, PhotoType photoType) {
        return UserBodyPhoto.builder()
                .user(userRepository.getReferenceById(userId))
                .photoType(photoType)
                .build();
    }

    private UserPhotoResponse toPhotoResponse(UserBodyPhoto photo) {
        URI downloadUrl = storageService.generateDownloadUrl(photo.getStorageKey(), StorageService.DEFAULT_TTL);
        return new UserPhotoResponse(photo.getPhotoType(), downloadUrl.toString());
    }
}