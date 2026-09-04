package com.fitcheck.feed.controller;

import com.fitcheck.feed.dto.FeedItemResponse;
import com.fitcheck.feed.dto.FeedPage;
import com.fitcheck.feed.dto.FeedResponse;
import com.fitcheck.feed.service.FeedGenerationService;
import com.fitcheck.feed.service.FeedProperties;
import com.fitcheck.feed.service.FeedResponseAssembler;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/feed")
@AllArgsConstructor
public class FeedController {

    private final FeedGenerationService feedGenerationService;
    private final FeedResponseAssembler feedResponseAssembler;
    private final FeedProperties feedProperties;

    @GetMapping
    public ResponseEntity<FeedResponse> getFeed(@AuthenticationPrincipal Jwt jwt,
                                                @RequestParam(required = false) String cursor) {
        UUID userId = UUID.fromString(jwt.getSubject());
        FeedPage page = feedGenerationService.getPage(userId, cursor, feedProperties.pageSize());

        List<FeedItemResponse> items = page.entries().stream()
                .map(feedResponseAssembler::toResponse)
                .toList();

        return ResponseEntity.ok(new FeedResponse(items, page.nextCursor()));
    }
}