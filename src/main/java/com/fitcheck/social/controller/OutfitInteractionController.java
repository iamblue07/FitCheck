package com.fitcheck.social.controller;

import com.fitcheck.social.dto.InteractionStateResponse;
import com.fitcheck.social.dto.SavedOutfitsResponse;
import com.fitcheck.social.enums.InteractionType;
import com.fitcheck.social.service.OutfitInteractionService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/outfits")
@AllArgsConstructor
public class OutfitInteractionController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final OutfitInteractionService outfitInteractionService;

    @PostMapping("/{outfitId}/like")
    public ResponseEntity<InteractionStateResponse> like(@AuthenticationPrincipal Jwt jwt,
                                                         @PathVariable UUID outfitId) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(outfitInteractionService.activate(userId, outfitId, InteractionType.LIKE));
    }

    @DeleteMapping("/{outfitId}/like")
    public ResponseEntity<InteractionStateResponse> unlike(@AuthenticationPrincipal Jwt jwt,
                                                           @PathVariable UUID outfitId) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(outfitInteractionService.deactivate(userId, outfitId, InteractionType.LIKE));
    }

    @PostMapping("/{outfitId}/save")
    public ResponseEntity<InteractionStateResponse> save(@AuthenticationPrincipal Jwt jwt,
                                                         @PathVariable UUID outfitId) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(outfitInteractionService.activate(userId, outfitId, InteractionType.SAVE));
    }

    @DeleteMapping("/{outfitId}/save")
    public ResponseEntity<InteractionStateResponse> unsave(@AuthenticationPrincipal Jwt jwt,
                                                           @PathVariable UUID outfitId) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(outfitInteractionService.deactivate(userId, outfitId, InteractionType.SAVE));
    }

    @GetMapping("/saved")
    public ResponseEntity<SavedOutfitsResponse> listSaved(@AuthenticationPrincipal Jwt jwt,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        UUID userId = UUID.fromString(jwt.getSubject());
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size));
        return ResponseEntity.ok(outfitInteractionService.listSaved(userId, pageable));
    }

    @GetMapping("/interactions/mine")
    public ResponseEntity<Set<UUID>> listInteractedOutfitIds(@AuthenticationPrincipal Jwt jwt,
                                                             @RequestParam InteractionType type) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(outfitInteractionService.listInteractedOutfitIds(userId, type));
    }

    private int clampSize(int size) {
        if (size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}