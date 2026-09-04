package com.fitcheck.feed.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record FeedCursor(BigDecimal rankScore, UUID id) {
}