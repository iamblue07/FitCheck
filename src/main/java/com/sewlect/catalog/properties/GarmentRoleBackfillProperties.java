package com.sewlect.catalog.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "catalog.role-backfill")
public record GarmentRoleBackfillProperties (
        boolean enabled
) {}
