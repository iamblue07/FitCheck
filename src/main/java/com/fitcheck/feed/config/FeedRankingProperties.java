package com.fitcheck.feed.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "feed.ranking")
public record FeedRankingProperties(
        BigDecimal personalizationWeight,
        BigDecimal styleWeight,
        BigDecimal budgetWeight
) {
}