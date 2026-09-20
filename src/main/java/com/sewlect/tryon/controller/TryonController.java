package com.sewlect.tryon.controller;

import com.sewlect.common.exception.dto.ErrorResponse;
import com.sewlect.common.openapi.annotation.StandardApiErrors;
import com.sewlect.tryon.dto.TryonStatusResponse;
import com.sewlect.tryon.dto.TryonSubmitRequest;
import com.sewlect.tryon.service.TryonRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Virtual try-on", description = "Asynchronous try-on jobs rendering the caller wearing an outfit")
public class TryonController {

    private final TryonRequestService tryonRequestService;

    @Operation(summary = "Submit a try-on job; returns immediately with a PENDING job, poll the status endpoint for the result")
    @StandardApiErrors
    @ApiResponse(responseCode = "429",
            description = "The caller's hourly try-on submission budget is exhausted",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping
    public ResponseEntity<TryonStatusResponse> submit(@AuthenticationPrincipal Jwt jwt,
                                                      @Valid @RequestBody TryonSubmitRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(tryonRequestService.submit(userId, request.outfitId()));
    }

    @Operation(summary = "Poll a try-on job; COMPLETE carries a presigned result-image URL, FAILED carries a reason")
    @StandardApiErrors
    @GetMapping("/{requestId}")
    public ResponseEntity<TryonStatusResponse> getStatus(@AuthenticationPrincipal Jwt jwt,
                                                         @PathVariable UUID requestId) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(tryonRequestService.getStatus(userId, requestId));
    }
}