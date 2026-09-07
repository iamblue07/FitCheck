package com.fitcheck.outfit.service;

import com.fitcheck.identity.entity.Role;
import com.fitcheck.identity.entity.User;
import com.fitcheck.outfit.config.OutfitPromptProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromptRateLimitResolverTest {

    private final OutfitPromptProperties properties = new OutfitPromptProperties(2, 3, 200, 200, 50);
    private final PromptRateLimitResolver resolver = new PromptRateLimitResolver(properties);

    @Test
    void resolveLimit_regularUser_returnsConfiguredLimit() {
        assertThat(resolver.resolveLimit(User.builder().role(Role.USER).build())).isEqualTo(200);
    }

    @Test
    void resolveLimit_adminUser_bypassesWithEffectivelyUnlimitedLimit() {
        assertThat(resolver.resolveLimit(User.builder().role(Role.ADMIN).build())).isEqualTo(Integer.MAX_VALUE);
    }
}