package com.fitcheck.outfit.controller;

import com.fitcheck.common.exception.RateLimitExceededException;
import com.fitcheck.common.ratelimit.InMemoryRateLimiter;
import com.fitcheck.identity.entity.Role;
import com.fitcheck.identity.entity.User;
import com.fitcheck.identity.service.UserReferenceQueryService;
import com.fitcheck.outfit.config.OutfitPromptProperties;
import com.fitcheck.outfit.dto.OutfitResponse;
import com.fitcheck.outfit.dto.PromptGenerationRequest;
import com.fitcheck.outfit.service.PromptOutfitGenerationService;
import com.fitcheck.outfit.service.PromptRateLimitResolver;
import com.fitcheck.outfit.service.PromptRefinementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromptControllerRateLimitTest {

    @Mock
    private PromptOutfitGenerationService promptOutfitGenerationService;

    @Mock
    private PromptRefinementService promptRefinementService;

    @Mock
    private UserReferenceQueryService userReferenceQueryService;

    private PromptController controller;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        InMemoryRateLimiter rateLimiter = new InMemoryRateLimiter(clock);
        OutfitPromptProperties properties = new OutfitPromptProperties(2, 3, 200, 200, 50);
        PromptRateLimitResolver rateLimitResolver = new PromptRateLimitResolver(properties);

        controller = new PromptController(
                promptOutfitGenerationService, promptRefinementService, rateLimiter,
                rateLimitResolver, userReferenceQueryService);
    }

    @Test
    void generate_201stRequestWithinTheHour_returns429ForARegularUser() {
        UUID userId = UUID.randomUUID();
        when(userReferenceQueryService.getById(userId)).thenReturn(User.builder().role(Role.USER).build());
        when(promptOutfitGenerationService.generate(any(), any(), anyBoolean()))
                .thenReturn(List.of(new OutfitResponse(UUID.randomUUID(), null, null, null)));
        Jwt jwt = jwtFor(userId);
        PromptGenerationRequest request = new PromptGenerationRequest("something casual", true);

        for (int i = 0; i < 200; i++) {
            controller.generate(jwt, request);
        }

        assertThatThrownBy(() -> controller.generate(jwt, request))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void generate_manyRequestsFromAnAdminAccount_neverTriggersRateLimit() {
        UUID adminId = UUID.randomUUID();
        when(userReferenceQueryService.getById(adminId)).thenReturn(User.builder().role(Role.ADMIN).build());
        when(promptOutfitGenerationService.generate(any(), any(), anyBoolean()))
                .thenReturn(List.of(new OutfitResponse(UUID.randomUUID(), null, null, null)));
        Jwt jwt = jwtFor(adminId);
        PromptGenerationRequest request = new PromptGenerationRequest("something casual", true);

        for (int i = 0; i < 250; i++) {
            controller.generate(jwt, request);
        }
    }

    private Jwt jwtFor(UUID userId) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(900))
                .claim("role", "USER")
                .build();
    }
}