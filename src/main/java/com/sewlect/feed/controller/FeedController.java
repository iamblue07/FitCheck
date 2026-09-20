package com.sewlect.feed.controller;

import com.sewlect.common.openapi.annotation.StandardApiErrors;
import com.sewlect.feed.dto.FeedItemResponse;
import com.sewlect.feed.dto.FeedPage;
import com.sewlect.feed.dto.FeedResponse;
import com.sewlect.feed.service.FeedGenerationService;
import com.sewlect.feed.properties.FeedProperties;
import com.sewlect.feed.support.FeedResponseAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Feed", description = "The caller's personalised, never-repeating outfit feed")
public class FeedController {

    private final FeedGenerationService feedGenerationService;
    private final FeedResponseAssembler feedResponseAssembler;
    private final FeedProperties feedProperties;

    @Operation(summary = "Get one page of the feed; pass the returned nextCursor to fetch the following page")
    @StandardApiErrors
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