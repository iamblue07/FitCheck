package com.fitcheck.identity.controller;

import com.fitcheck.identity.dto.StyleTagResponse;
import com.fitcheck.identity.dto.UpdateStylePreferencesRequest;
import com.fitcheck.identity.dto.UserProfileResponse;
import com.fitcheck.identity.dto.UserProfileUpdateRequest;
import com.fitcheck.identity.service.ProfileService;
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
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping("/profile")
    public ResponseEntity<UserProfileResponse> getProfile(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(profileService.getProfile(userId));
    }

    @PutMapping("/profile")
    public ResponseEntity<UserProfileResponse> updateProfile(@AuthenticationPrincipal Jwt jwt,
                                                             @Valid @RequestBody UserProfileUpdateRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(profileService.updateProfile(userId, request));
    }

    @PutMapping("/style-preferences")
    public ResponseEntity<List<StyleTagResponse>> updateStylePreferences(@AuthenticationPrincipal Jwt jwt,
                                                                         @Valid @RequestBody UpdateStylePreferencesRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(profileService.updateStylePreferences(userId, request.styleTagIds()));
    }
}