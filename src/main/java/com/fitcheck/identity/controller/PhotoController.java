package com.fitcheck.identity.controller;

import com.fitcheck.common.openapi.annotation.StandardApiErrors;
import com.fitcheck.identity.dto.ConfirmPhotoUploadRequest;
import com.fitcheck.identity.dto.PresignedUploadRequest;
import com.fitcheck.identity.dto.PresignedUploadResponse;
import com.fitcheck.identity.dto.UserPhotoResponse;
import com.fitcheck.identity.service.PhotoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Body photos", description = "Front and back body photos used as the base image for virtual try-on")
public class PhotoController {

    private final PhotoService photoService;

    @Operation(summary = "Issue a presigned PUT URL so the client uploads the photo straight to object storage")
    @StandardApiErrors
    @PostMapping("/upload-url")
    public ResponseEntity<PresignedUploadResponse> generateUploadUrl(@AuthenticationPrincipal Jwt jwt,
                                                                     @Valid @RequestBody PresignedUploadRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(photoService.generateUploadUrl(userId, request.photoType()));
    }

    @Operation(summary = "Confirm a presigned upload actually landed, and record the photo against the caller")
    @StandardApiErrors
    @PostMapping("/confirm")
    public ResponseEntity<UserPhotoResponse> confirmUpload(@AuthenticationPrincipal Jwt jwt,
                                                           @Valid @RequestBody ConfirmPhotoUploadRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(photoService.confirmUpload(userId, request.photoType()));
    }

    @Operation(summary = "List the caller's body photos with presigned download URLs")
    @StandardApiErrors
    @GetMapping
    public ResponseEntity<List<UserPhotoResponse>> listPhotos(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(photoService.listPhotos(userId));
    }
}