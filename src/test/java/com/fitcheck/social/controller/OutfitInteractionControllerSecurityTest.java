package com.fitcheck.social.controller;

import com.fitcheck.common.ratelimit.InMemoryRateLimiter;
import com.fitcheck.common.security.config.JwtConfig;
import com.fitcheck.common.security.config.SecurityConfig;
import com.fitcheck.common.config.CommonBeansConfig;
import com.fitcheck.common.exception.support.ErrorResponseFactory;
import com.fitcheck.common.security.handler.RestAccessDeniedHandler;
import com.fitcheck.common.security.handler.RestAuthenticationEntryPoint;
import com.fitcheck.identity.service.AppUserDetailsService;
import com.fitcheck.social.dto.InteractionStateResponse;
import com.fitcheck.social.dto.SavedOutfitsResponse;
import com.fitcheck.social.enums.InteractionType;
import com.fitcheck.social.service.OutfitInteractionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OutfitInteractionController.class)
@Import({SecurityConfig.class, JwtConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        ErrorResponseFactory.class, CommonBeansConfig.class})
@TestPropertySource(properties = {
        "jwt.secret=" + OutfitInteractionControllerSecurityTest.TEST_JWT_SECRET,
        "jwt.access-expiration=900000",
        "jwt.refresh-expiration=604800000"
})
class OutfitInteractionControllerSecurityTest {

    static final String TEST_JWT_SECRET = "test-secret-key-at-least-32-characters-long-xxxx";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InMemoryRateLimiter inMemoryRateLimiter;

    @MockitoBean
    private OutfitInteractionService outfitInteractionService;

    @MockitoBean
    private AppUserDetailsService appUserDetailsService;

    @Test
    void like_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/outfits/{outfitId}/like", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void like_validToken_isAuthorized() throws Exception {
        UUID outfitId = UUID.randomUUID();
        when(outfitInteractionService.activate(any(), eq(outfitId), eq(InteractionType.LIKE)))
                .thenReturn(new InteractionStateResponse(true));

        mockMvc.perform(post("/api/v1/outfits/{outfitId}/like", outfitId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateTestAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void unlike_noToken_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/outfits/{outfitId}/like", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void unlike_validToken_isAuthorized() throws Exception {
        UUID outfitId = UUID.randomUUID();
        when(outfitInteractionService.deactivate(any(), eq(outfitId), eq(InteractionType.LIKE)))
                .thenReturn(new InteractionStateResponse(false));

        mockMvc.perform(delete("/api/v1/outfits/{outfitId}/like", outfitId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateTestAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void save_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/outfits/{outfitId}/save", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void save_validToken_isAuthorized() throws Exception {
        UUID outfitId = UUID.randomUUID();
        when(outfitInteractionService.activate(any(), eq(outfitId), eq(InteractionType.SAVE)))
                .thenReturn(new InteractionStateResponse(true));

        mockMvc.perform(post("/api/v1/outfits/{outfitId}/save", outfitId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateTestAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void unsave_noToken_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/outfits/{outfitId}/save", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void unsave_validToken_isAuthorized() throws Exception {
        UUID outfitId = UUID.randomUUID();
        when(outfitInteractionService.deactivate(any(), eq(outfitId), eq(InteractionType.SAVE)))
                .thenReturn(new InteractionStateResponse(false));

        mockMvc.perform(delete("/api/v1/outfits/{outfitId}/save", outfitId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateTestAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void listSaved_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/outfits/saved"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void listSaved_validToken_isAuthorized() throws Exception {
        when(outfitInteractionService.listSaved(any(), any()))
                .thenReturn(new SavedOutfitsResponse(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/api/v1/outfits/saved")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateTestAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void listInteractedOutfitIds_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/outfits/interactions/mine").param("type", "LIKE"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void listInteractedOutfitIds_validToken_isAuthorized() throws Exception {
        UUID likedOutfitId = UUID.randomUUID();
        when(outfitInteractionService.listInteractedOutfitIds(any(), eq(InteractionType.LIKE)))
                .thenReturn(Set.of(likedOutfitId));

        mockMvc.perform(get("/api/v1/outfits/interactions/mine").param("type", "LIKE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateTestAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value(likedOutfitId.toString()));
    }

    private String generateTestAccessToken() {
        SecretKey secretKey = new SecretKeySpec(TEST_JWT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder jwtEncoder = NimbusJwtEncoder.withSecretKey(secretKey).build();

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(15)))
                .claim("email", "test@example.com")
                .claim("role", "USER")
                .issuer("https://fitcheck.local")
                .audience(List.of("fitcheck-api"))
                .build();
        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();

        return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
    }
}