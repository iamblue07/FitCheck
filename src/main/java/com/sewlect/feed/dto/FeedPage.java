package com.sewlect.feed.dto;

import com.sewlect.feed.entity.FeedEntry;

import java.util.List;

public record FeedPage(List<FeedEntry> entries, String nextCursor) {
}