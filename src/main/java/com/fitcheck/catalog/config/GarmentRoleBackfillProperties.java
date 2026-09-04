package com.fitcheck.catalog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "catalog.role-backfill")
public record GarmentRoleBackfillProperties (
        boolean enabled
) {}
