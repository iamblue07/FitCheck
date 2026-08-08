package com.fitcheck.identity.controller;

import com.fitcheck.identity.dto.StyleTagResponse;
import com.fitcheck.identity.service.StyleTagService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/style-tags")
@AllArgsConstructor
public class StyleTagController {

    private final StyleTagService styleTagService;

    @GetMapping
    public ResponseEntity<List<StyleTagResponse>> listAll() {
        return ResponseEntity.ok(styleTagService.listAll());
    }
}