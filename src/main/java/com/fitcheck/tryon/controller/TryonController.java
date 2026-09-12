package com.fitcheck.tryon.controller;

import com.fitcheck.tryon.dto.TryonStatusResponse;
import com.fitcheck.tryon.dto.TryonSubmitRequest;
import com.fitcheck.tryon.service.TryonRequestService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tryon")
@AllArgsConstructor
public class TryonController {

    private final TryonRequestService tryonRequestService;

    @PostMapping
    public ResponseEntity<TryonStatusResponse> submit(@AuthenticationPrincipal Jwt jwt,
                                                      @Valid @RequestBody TryonSubmitRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(tryonRequestService.submit(userId, request.outfitId()));
    }

    @GetMapping("/{requestId}")
    public ResponseEntity<TryonStatusResponse> getStatus(@AuthenticationPrincipal Jwt jwt,
                                                         @PathVariable UUID requestId) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(tryonRequestService.getStatus(userId, requestId));
    }
}