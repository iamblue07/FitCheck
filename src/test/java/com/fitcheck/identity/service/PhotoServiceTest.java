package com.fitcheck.identity.service;

import com.fitcheck.common.exception.BadRequestException;
import com.fitcheck.common.exception.ExternalServiceException;
import com.fitcheck.common.storage.StorageKeys;
import com.fitcheck.common.storage.StorageService;
import com.fitcheck.identity.dto.PresignedUploadResponse;
import com.fitcheck.identity.dto.UserPhotoResponse;
import com.fitcheck.identity.entity.PhotoType;
import com.fitcheck.identity.entity.User;
import com.fitcheck.identity.entity.UserBodyPhoto;
import com.fitcheck.identity.repository.UserBodyPhotoRepository;
import com.fitcheck.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhotoServiceTest {

    @Mock
    private UserBodyPhotoRepository userBodyPhotoRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private PhotoService photoService;

    @Test
    void generateUploadUrl_buildsKeyViaStorageKeysAndDelegatesToStorageService() {
        UUID userId = UUID.randomUUID();
        String expectedKey = StorageKeys.bodyPhotoKey(userId, "front");
        URI presignedUrl = URI.create("https://r2.example.com/presigned-put");

        when(storageService.generateUploadUrl(expectedKey, "image/jpeg", StorageService.DEFAULT_TTL))
                .thenReturn(presignedUrl);

        PresignedUploadResponse response = photoService.generateUploadUrl(userId, PhotoType.FRONT);

        assertThat(response.uploadUrl()).isEqualTo(presignedUrl.toString());
        assertThat(response.expiresAt())
                .isCloseTo(LocalDateTime.now().plus(StorageService.DEFAULT_TTL), within(5, ChronoUnit.SECONDS));

        verify(storageService).generateUploadUrl(expectedKey, "image/jpeg", StorageService.DEFAULT_TTL);
    }

    @Test
    void confirmUpload_objectMissingFromStorage_throwsBadRequestException() {
        UUID userId = UUID.randomUUID();
        String key = StorageKeys.bodyPhotoKey(userId, "front");

        when(storageService.exists(key)).thenReturn(false);

        assertThatThrownBy(() -> photoService.confirmUpload(userId, PhotoType.FRONT))
                .isInstanceOf(BadRequestException.class);

        verify(userBodyPhotoRepository, never()).findByUserIdAndPhotoType(any(), any());
        verify(userBodyPhotoRepository, never()).save(any());
    }

    @Test
    void confirmUpload_newPhotoType_insertsRow() {
        UUID userId = UUID.randomUUID();
        String key = StorageKeys.bodyPhotoKey(userId, "front");
        User userRef = User.builder().id(userId).build();
        URI downloadUrl = URI.create("https://r2.example.com/presigned-get");

        when(storageService.exists(key)).thenReturn(true);
        when(userBodyPhotoRepository.findByUserIdAndPhotoType(userId, PhotoType.FRONT)).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(userId)).thenReturn(userRef);
        when(storageService.generateDownloadUrl(key, StorageService.DEFAULT_TTL)).thenReturn(downloadUrl);

        UserPhotoResponse response = photoService.confirmUpload(userId, PhotoType.FRONT);

        ArgumentCaptor<UserBodyPhoto> captor = ArgumentCaptor.forClass(UserBodyPhoto.class);
        verify(userBodyPhotoRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isEqualTo(userRef);
        assertThat(captor.getValue().getPhotoType()).isEqualTo(PhotoType.FRONT);
        assertThat(captor.getValue().getStorageKey()).isEqualTo(key);

        assertThat(response.photoType()).isEqualTo(PhotoType.FRONT);
        assertThat(response.downloadUrl()).isEqualTo(downloadUrl.toString());
    }

    @Test
    void confirmUpload_existingPhotoType_updatesRowRatherThanInserting() {
        UUID userId = UUID.randomUUID();
        String key = StorageKeys.bodyPhotoKey(userId, "front");
        UserBodyPhoto existingPhoto = UserBodyPhoto.builder()
                .user(User.builder().id(userId).build())
                .photoType(PhotoType.FRONT)
                .storageKey(key)
                .build();
        URI downloadUrl = URI.create("https://r2.example.com/presigned-get");

        when(storageService.exists(key)).thenReturn(true);
        when(userBodyPhotoRepository.findByUserIdAndPhotoType(userId, PhotoType.FRONT))
                .thenReturn(Optional.of(existingPhoto));
        when(storageService.generateDownloadUrl(key, StorageService.DEFAULT_TTL)).thenReturn(downloadUrl);

        photoService.confirmUpload(userId, PhotoType.FRONT);

        verify(userBodyPhotoRepository).save(existingPhoto);
        verify(userRepository, never()).getReferenceById(any());
    }

    @Test
    void listPhotos_generatesFreshPresignedUrlPerPhoto() {
        UUID userId = UUID.randomUUID();
        String frontKey = StorageKeys.bodyPhotoKey(userId, "front");
        String backKey = StorageKeys.bodyPhotoKey(userId, "back");

        UserBodyPhoto frontPhoto = UserBodyPhoto.builder()
                .user(User.builder().id(userId).build())
                .photoType(PhotoType.FRONT)
                .storageKey(frontKey)
                .build();
        UserBodyPhoto backPhoto = UserBodyPhoto.builder()
                .user(User.builder().id(userId).build())
                .photoType(PhotoType.BACK)
                .storageKey(backKey)
                .build();

        URI frontUrl = URI.create("https://r2.example.com/front-get");
        URI backUrl = URI.create("https://r2.example.com/back-get");

        when(userBodyPhotoRepository.findAllByUserId(userId)).thenReturn(List.of(frontPhoto, backPhoto));
        when(storageService.generateDownloadUrl(frontKey, StorageService.DEFAULT_TTL)).thenReturn(frontUrl);
        when(storageService.generateDownloadUrl(backKey, StorageService.DEFAULT_TTL)).thenReturn(backUrl);

        List<UserPhotoResponse> result = photoService.listPhotos(userId);

        assertThat(result).containsExactlyInAnyOrder(
                new UserPhotoResponse(PhotoType.FRONT, frontUrl.toString()),
                new UserPhotoResponse(PhotoType.BACK, backUrl.toString()));

        verify(storageService).generateDownloadUrl(frontKey, StorageService.DEFAULT_TTL);
        verify(storageService).generateDownloadUrl(backKey, StorageService.DEFAULT_TTL);
    }

    @Test
    void generateUploadUrl_backPhotoType_buildsKeyWithBackSuffix() {
        UUID userId = UUID.randomUUID();
        String expectedKey = StorageKeys.bodyPhotoKey(userId, "back");
        URI presignedUrl = URI.create("https://r2.example.com/presigned-put-back");

        when(storageService.generateUploadUrl(expectedKey, "image/jpeg", StorageService.DEFAULT_TTL))
                .thenReturn(presignedUrl);

        PresignedUploadResponse response = photoService.generateUploadUrl(userId, PhotoType.BACK);

        assertThat(response.uploadUrl()).isEqualTo(presignedUrl.toString());
        verify(storageService).generateUploadUrl(expectedKey, "image/jpeg", StorageService.DEFAULT_TTL);
    }

    @Test
    void confirmUpload_reUpload_keepsSameDeterministicStorageKey() {
        UUID userId = UUID.randomUUID();
        String key = StorageKeys.bodyPhotoKey(userId, "front");
        UserBodyPhoto existingPhoto = UserBodyPhoto.builder()
                .user(User.builder().id(userId).build())
                .photoType(PhotoType.FRONT)
                .storageKey(key)
                .build();

        when(storageService.exists(key)).thenReturn(true);
        when(userBodyPhotoRepository.findByUserIdAndPhotoType(userId, PhotoType.FRONT))
                .thenReturn(Optional.of(existingPhoto));
        when(storageService.generateDownloadUrl(key, StorageService.DEFAULT_TTL))
                .thenReturn(URI.create("https://r2.example.com/get"));

        photoService.confirmUpload(userId, PhotoType.FRONT);

        assertThat(existingPhoto.getStorageKey()).isEqualTo(key);
    }

    @Test
    void listPhotos_noPhotosUploaded_returnsEmptyList() {
        UUID userId = UUID.randomUUID();
        when(userBodyPhotoRepository.findAllByUserId(userId)).thenReturn(List.of());

        List<UserPhotoResponse> result = photoService.listPhotos(userId);

        assertThat(result).isEmpty();
        verify(storageService, never()).generateDownloadUrl(any(), any());
    }

    @Test
    void confirmUpload_storageServiceExistsCheckFails_propagatesExternalServiceException() {
        UUID userId = UUID.randomUUID();
        String key = StorageKeys.bodyPhotoKey(userId, "front");

        when(storageService.exists(key)).thenThrow(new ExternalServiceException("R2 unreachable"));

        assertThatThrownBy(() -> photoService.confirmUpload(userId, PhotoType.FRONT))
                .isInstanceOf(ExternalServiceException.class);

        verify(userBodyPhotoRepository, never()).save(any());
    }
}