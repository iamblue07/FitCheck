package com.sewlect.outfit.support;

import com.sewlect.identity.enums.Role;
import com.sewlect.identity.entity.User;
import com.sewlect.outfit.properties.OutfitPromptProperties;
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