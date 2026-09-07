package com.fitcheck.outfit.controller;

import com.fitcheck.common.ratelimit.InMemoryRateLimiter;
import com.fitcheck.common.security.JwtConfig;
import com.fitcheck.common.security.RestAccessDeniedHandler;
import com.fitcheck.common.security.RestAuthenticationEntryPoint;
import com.fitcheck.common.security.SecurityConfig;
import com.fitcheck.identity.entity.Role;
import com.fitcheck.identity.entity.User;
import com.fitcheck.identity.service.AppUserDetailsService;
import com.fitcheck.identity.service.UserReferenceQueryService;
import com.fitcheck.outfit.dto.OutfitResponse;
import com.fitcheck.outfit.service.PromptOutfitGenerationService;
import com.fitcheck.outfit.service.PromptRateLimitResolver;
import com.fitcheck.outfit.service.PromptRefinementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PromptController.class)
@Import({SecurityConfig.class, JwtConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
@TestPropertySource(properties = {
        "jwt.secret=" + PromptControllerSecurityTest.TEST_JWT_SECRET,
        "jwt.access-expiration=900000",
        "jwt.refresh-expiration=604800000"
})
class PromptControllerSecurityTest {

    static final String TEST_JWT_SECRET = "test-secret-key-at-least-32-characters-long-xxxx";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PromptOutfitGenerationService promptOutfitGenerationService;

    @MockitoBean
    private PromptRefinementService promptRefinementService;

    @MockitoBean
    private InMemoryRateLimiter inMemoryRateLimiter;

    @MockitoBean
    private PromptRateLimitResolver promptRateLimitResolver;

    @MockitoBean
    private UserReferenceQueryService userReferenceQueryService;

    @MockitoBean
    private AppUserDetailsService appUserDetailsService;

    @Test
    void generate_missingAuthorizationHeader_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/outfits/prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\": \"something casual\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void generate_validTokenUnderLimit_returns200WithMappedBody() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID outfitId = UUID.randomUUID();
        when(userReferenceQueryService.getById(any())).thenReturn(User.builder().role(Role.USER).build());
        when(promptRateLimitResolver.resolveLimit(any())).thenReturn(200);
        when(inMemoryRateLimiter.tryConsume(any(), any(), anyInt(), any())).thenReturn(true);
        when(promptOutfitGenerationService.generate(any(), any(), anyBoolean()))
                .thenReturn(List.of(new OutfitResponse(outfitId, null, new BigDecimal("99.99"), List.of())));

        mockMvc.perform(post("/api/v1/outfits/prompt")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateTestAccessToken(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\": \"something casual for a Tuesday\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].outfitId").value(outfitId.toString()))
                .andExpect(jsonPath("$[0].totalPrice").value(99.99));
    }

    @Test
    void generate_rateLimitExceeded_returns429() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userReferenceQueryService.getById(any())).thenReturn(User.builder().role(Role.USER).build());
        when(promptRateLimitResolver.resolveLimit(any())).thenReturn(200);
        when(inMemoryRateLimiter.tryConsume(any(), any(), anyInt(), any())).thenReturn(false);

        mockMvc.perform(post("/api/v1/outfits/prompt")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateTestAccessToken(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\": \"something casual\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    void refine_missingAuthorizationHeader_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/outfits/{outfitId}/items/{itemId}/refine", UUID.randomUUID(), UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\": \"make it dressier\"}"))
                .andExpect(status().isUnauthorized());
    }

    private String generateTestAccessToken(UUID userId) {
        SecretKey secretKey = new SecretKeySpec(TEST_JWT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder jwtEncoder = NimbusJwtEncoder.withSecretKey(secretKey).build();

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(15)))
                .claim("email", "test@example.com")
                .claim("role", "USER")
                .build();
        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();

        return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
    }
}