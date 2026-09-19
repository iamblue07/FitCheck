package com.fitcheck.identity.controller;

import com.fitcheck.common.openapi.annotation.StandardApiErrors;
import com.fitcheck.identity.dto.StyleTagResponse;
import com.fitcheck.identity.service.StyleTagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/style-tags")
@AllArgsConstructor
@Tag(name = "Style tags", description = "The fixed twelve-tag style vocabulary shared by users and products")
public class StyleTagController {

    private final StyleTagService styleTagService;

    @Operation(summary = "List every style tag available for profile preferences")
    @StandardApiErrors
    @GetMapping
    public ResponseEntity<List<StyleTagResponse>> listAll() {
        return ResponseEntity.ok(styleTagService.listAll());
    }
}