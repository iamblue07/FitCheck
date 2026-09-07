package com.fitcheck.outfit.service;

import com.fitcheck.identity.entity.Role;
import com.fitcheck.identity.entity.User;
import com.fitcheck.outfit.config.OutfitPromptProperties;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class PromptRateLimitResolver {

    private final OutfitPromptProperties properties;

    public int resolveLimit(User user) {
        return user.getRole() == Role.ADMIN ? Integer.MAX_VALUE : properties.rateLimitPerHour();
    }
}