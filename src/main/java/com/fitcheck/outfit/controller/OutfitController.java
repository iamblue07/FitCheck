package com.fitcheck.outfit.controller;

import com.fitcheck.outfit.dto.OutfitResponse;
import com.fitcheck.outfit.entity.Outfit;
import com.fitcheck.outfit.service.OutfitItemQueryService;
import com.fitcheck.outfit.support.OutfitResponseAssembler;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/outfits")
@AllArgsConstructor
public class OutfitController {

    private final OutfitItemQueryService outfitItemQueryService;
    private final OutfitResponseAssembler outfitResponseAssembler;

    @GetMapping("/{outfitId}")
    public ResponseEntity<OutfitResponse> getOutfit(@PathVariable UUID outfitId) {
        Outfit outfit = outfitItemQueryService.getById(outfitId);
        return ResponseEntity.ok(outfitResponseAssembler.toResponse(outfit));
    }
}