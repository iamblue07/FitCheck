package com.fitcheck.identity.controller;

import com.fitcheck.identity.dto.ConfirmPhotoUploadRequest;
import com.fitcheck.identity.dto.PresignedUploadRequest;
import com.fitcheck.identity.dto.PresignedUploadResponse;
import com.fitcheck.identity.dto.UserPhotoResponse;
import com.fitcheck.identity.service.PhotoService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me/photos")
@AllArgsConstructor
public class PhotoController {

    private final PhotoService photoService;

    @PostMapping("/upload-url")
    public ResponseEntity<PresignedUploadResponse> generateUploadUrl(@AuthenticationPrincipal Jwt jwt,
                                                                     @Valid @RequestBody PresignedUploadRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(photoService.generateUploadUrl(userId, request.photoType()));
    }

    @PostMapping("/confirm")
    public ResponseEntity<UserPhotoResponse> confirmUpload(@AuthenticationPrincipal Jwt jwt,
                                                           @Valid @RequestBody ConfirmPhotoUploadRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(photoService.confirmUpload(userId, request.photoType()));
    }

    @GetMapping
    public ResponseEntity<List<UserPhotoResponse>> listPhotos(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(photoService.listPhotos(userId));
    }
}