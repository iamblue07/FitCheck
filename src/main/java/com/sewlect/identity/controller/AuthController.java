package com.sewlect.identity.controller;

import com.sewlect.common.openapi.annotation.StandardApiErrors;
import com.sewlect.identity.dto.AuthResponse;
import com.sewlect.identity.dto.LoginRequest;
import com.sewlect.identity.dto.LogoutRequest;
import com.sewlect.identity.dto.RefreshRequest;
import com.sewlect.identity.dto.RegisterRequest;
import com.sewlect.identity.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Registration, login, refresh-token rotation and logout")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Register a new account and return the first access/refresh token pair")
    @SecurityRequirements
    @StandardApiErrors
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Exchange email and password for an access/refresh token pair")
    @SecurityRequirements
    @StandardApiErrors
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Rotate a refresh token into a new access/refresh pair; reusing a revoked token revokes every token for that user")
    @SecurityRequirements
    @StandardApiErrors
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        AuthResponse response = authService.refresh(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Revoke the supplied refresh token")
    @StandardApiErrors
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }
}