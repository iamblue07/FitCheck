package com.fitcheck.identity.controller;

import com.fitcheck.common.openapi.annotation.StandardApiErrors;
import com.fitcheck.identity.dto.StyleTagResponse;
import com.fitcheck.identity.dto.UpdateStylePreferencesRequest;
import com.fitcheck.identity.dto.UserProfileResponse;
import com.fitcheck.identity.dto.UserProfileUpdateRequest;
import com.fitcheck.identity.service.ProfileService;
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
@RequestMapping("/api/v1/users/me")
@AllArgsConstructor
@Tag(name = "Profile", description = "The caller's own profile and style-tag preferences")
public class ProfileController {

    private final ProfileService profileService;

    @Operation(summary = "Get the caller's profile")
    @StandardApiErrors
    @GetMapping("/profile")
    public ResponseEntity<UserProfileResponse> getProfile(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(profileService.getProfile(userId));
    }

    @Operation(summary = "Partially update the caller's profile; omitted fields are left unchanged")
    @StandardApiErrors
    @PutMapping("/profile")
    public ResponseEntity<UserProfileResponse> updateProfile(@AuthenticationPrincipal Jwt jwt,
                                                             @Valid @RequestBody UserProfileUpdateRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(profileService.updateProfile(userId, request));
    }

    @Operation(summary = "Replace the caller's style-tag preferences wholesale with the supplied set")
    @StandardApiErrors
    @PutMapping("/style-preferences")
    public ResponseEntity<List<StyleTagResponse>> updateStylePreferences(@AuthenticationPrincipal Jwt jwt,
                                                                         @Valid @RequestBody UpdateStylePreferencesRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(profileService.updateStylePreferences(userId, request.styleTagIds()));
    }
}