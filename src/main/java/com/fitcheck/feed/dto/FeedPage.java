package com.fitcheck.feed.dto;

import com.fitcheck.feed.entity.FeedEntry;

import java.util.List;

public record FeedPage(List<FeedEntry> entries, String nextCursor) {
}