package com.fitcheck.outfit.controller;

import com.fitcheck.common.exception.RateLimitExceededException;
import com.fitcheck.common.ratelimit.InMemoryRateLimiter;
import com.fitcheck.identity.entity.User;
import com.fitcheck.identity.service.UserReferenceQueryService;
import com.fitcheck.outfit.dto.AlternativeCandidateResponse;
import com.fitcheck.outfit.dto.OutfitResponse;
import com.fitcheck.outfit.dto.PromptGenerationRequest;
import com.fitcheck.outfit.dto.PromptRefinementRequest;
import com.fitcheck.outfit.service.PromptOutfitGenerationService;
import com.fitcheck.outfit.service.PromptRateLimitResolver;
import com.fitcheck.outfit.service.PromptRefinementService;
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
public class PromptController {

    private static final String GENERATION_OPERATION_KEY = "outfit-prompt-generation";
    private static final String REFINEMENT_OPERATION_KEY = "outfit-prompt-refinement";

    private final PromptOutfitGenerationService promptOutfitGenerationService;
    private final PromptRefinementService promptRefinementService;
    private final InMemoryRateLimiter inMemoryRateLimiter;
    private final PromptRateLimitResolver promptRateLimitResolver;
    private final UserReferenceQueryService userReferenceQueryService;

    @PostMapping("/prompt")
    public ResponseEntity<List<OutfitResponse>> generate(@AuthenticationPrincipal Jwt jwt,
                                                         @Valid @RequestBody PromptGenerationRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        enforceRateLimit(userId, GENERATION_OPERATION_KEY);
        boolean matchProfile = request.matchProfile() == null || request.matchProfile();
        return ResponseEntity.ok(promptOutfitGenerationService.generate(userId, request.prompt(), matchProfile));
    }

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
        boolean consumed = inMemoryRateLimiter.tryConsume(userId, operationKey, limit, Duration.ofHours(1));
        if (!consumed) {
            throw new RateLimitExceededException("Rate limit exceeded for this operation - try again later");
        }
    }
}