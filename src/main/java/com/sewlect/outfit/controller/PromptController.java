package com.sewlect.outfit.controller;

import com.sewlect.common.exception.RateLimitExceededException;
import com.sewlect.common.exception.dto.ErrorResponse;
import com.sewlect.common.openapi.annotation.StandardApiErrors;
import com.sewlect.common.ratelimit.RateLimiter;
import com.sewlect.identity.entity.User;
import com.sewlect.identity.service.UserReferenceQueryService;
import com.sewlect.outfit.dto.AlternativeCandidateResponse;
import com.sewlect.outfit.dto.OutfitResponse;
import com.sewlect.outfit.dto.PromptGenerationRequest;
import com.sewlect.outfit.dto.PromptRefinementRequest;
import com.sewlect.outfit.service.PromptOutfitGenerationService;
import com.sewlect.outfit.support.PromptRateLimitResolver;
import com.sewlect.outfit.service.PromptRefinementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/outfits")
@AllArgsConstructor
@Tag(name = "AI prompt", description = "Free-text prompt to a batch of outfits, and prompt-driven refinement of one garment slot")
public class PromptController {

    private static final String GENERATION_OPERATION_KEY = "outfit-prompt-generation";
    private static final String REFINEMENT_OPERATION_KEY = "outfit-prompt-refinement";

    private final PromptOutfitGenerationService promptOutfitGenerationService;
    private final PromptRefinementService promptRefinementService;
    private final RateLimiter rateLimiter;
    private final PromptRateLimitResolver promptRateLimitResolver;
    private final UserReferenceQueryService userReferenceQueryService;

    @Operation(summary = "Generate a batch of outfits from a free-text prompt; matchProfile=false makes the AI infer gender and budget from the prompt instead of the caller's profile")
    @StandardApiErrors
    @ApiResponse(responseCode = "429",
            description = "The caller's hourly prompt-generation budget is exhausted",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/prompt")
    public ResponseEntity<List<OutfitResponse>> generate(@AuthenticationPrincipal Jwt jwt,
                                                         @Valid @RequestBody PromptGenerationRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        enforceRateLimit(userId, GENERATION_OPERATION_KEY);
        boolean matchProfile = request.matchProfile() == null || request.matchProfile();
        return ResponseEntity.ok(promptOutfitGenerationService.generate(userId, request.prompt(), matchProfile));
    }

    @Operation(summary = "Browse prompt-driven alternatives for one garment slot; read-only, commit the choice through the swap endpoint")
    @StandardApiErrors
    @ApiResponse(responseCode = "429",
            description = "The caller's hourly prompt-refinement budget is exhausted",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/{outfitId}/items/{itemId}/refine")
    public ResponseEntity<List<AlternativeCandidateResponse>> refine(@AuthenticationPrincipal Jwt jwt,
                                                                     @PathVariable UUID outfitId,
                                                                     @PathVariable UUID itemId,
                                                                     @Valid @RequestBody PromptRefinementRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        enforceRateLimit(userId, REFINEMENT_OPERATION_KEY);
        return ResponseEntity.ok(promptRefinementService.refine(outfitId, itemId, userId, request.prompt()));
    }

    private void enforceRateLimit(UUID userId, String operationKey) {
        User user = userReferenceQueryService.getById(userId);
        int limit = promptRateLimitResolver.resolveLimit(user);
        boolean consumed = rateLimiter.tryConsume(userId.toString(), operationKey, limit, Duration.ofHours(1));
        if (!consumed) {
            throw new RateLimitExceededException("Rate limit exceeded for this operation - try again later");
        }
    }
}