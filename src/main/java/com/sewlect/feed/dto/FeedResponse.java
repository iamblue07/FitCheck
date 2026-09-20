package com.sewlect.feed.dto;

import java.util.List;

public record FeedResponse(
        List<FeedItemResponse> items,
        String nextCursor
) {
}