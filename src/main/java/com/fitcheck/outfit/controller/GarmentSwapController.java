package com.fitcheck.outfit.controller;

import com.fitcheck.outfit.dto.AlternativeCandidateResponse;
import com.fitcheck.outfit.dto.OutfitResponse;
import com.fitcheck.outfit.dto.SwapRequest;
import com.fitcheck.outfit.service.GarmentSwapService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/outfits/{outfitId}/items/{itemId}")
@AllArgsConstructor
public class GarmentSwapController {

    private final GarmentSwapService garmentSwapService;

    @GetMapping("/alternatives")
    public ResponseEntity<List<AlternativeCandidateResponse>> listAlternatives(@AuthenticationPrincipal Jwt jwt,
                                                                               @PathVariable UUID outfitId,
                                                                               @PathVariable UUID itemId) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(garmentSwapService.listAlternatives(outfitId, itemId, userId));
    }

    @PostMapping("/swap")
    public ResponseEntity<OutfitResponse> swap(@AuthenticationPrincipal Jwt jwt,
                                               @PathVariable UUID outfitId,
                                               @PathVariable UUID itemId,
                                               @Valid @RequestBody SwapRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(garmentSwapService.swap(outfitId, itemId, request.productId(), userId));
    }
}