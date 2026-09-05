package com.fitcheck.outfit.controller;

import com.fitcheck.common.security.JwtConfig;
import com.fitcheck.common.security.RestAccessDeniedHandler;
import com.fitcheck.common.security.RestAuthenticationEntryPoint;
import com.fitcheck.common.security.SecurityConfig;
import com.fitcheck.common.taxonomy.GarmentRole;
import com.fitcheck.identity.service.AppUserDetailsService;
import com.fitcheck.outfit.dto.AlternativeCandidateResponse;
import com.fitcheck.outfit.dto.CompatibilityScoreBreakdown;
import com.fitcheck.outfit.dto.OutfitItemView;
import com.fitcheck.outfit.dto.OutfitResponse;
import com.fitcheck.outfit.service.GarmentSwapService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GarmentSwapController.class)
@Import({SecurityConfig.class, JwtConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
@TestPropertySource(properties = {
        "jwt.secret=" + GarmentSwapControllerSecurityTest.TEST_JWT_SECRET,
        "jwt.access-expiration=900000",
        "jwt.refresh-expiration=604800000"
})
class GarmentSwapControllerSecurityTest {

    static final String TEST_JWT_SECRET = "test-secret-key-at-least-32-characters-long-xxxx";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GarmentSwapService garmentSwapService;

    @MockitoBean
    private AppUserDetailsService appUserDetailsService;

    @Test
    void listAlternatives_missingAuthorizationHeader_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/outfits/{outfitId}/items/{itemId}/alternatives",
                        UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void listAlternatives_validToken_returns200WithMappedBodyAndDerivesUserIdFromJwtSubject() throws Exception {
        UUID outfitId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        CompatibilityScoreBreakdown breakdown = new CompatibilityScoreBreakdown(
                new BigDecimal("0.8"), new BigDecimal("0.7"), new BigDecimal("0.9"),
                new BigDecimal("0.85"), new BigDecimal("0.82"));
        List<AlternativeCandidateResponse> mockResponse = List.of(
                new AlternativeCandidateResponse(candidateId, "Slim Fit Tee", "https://example.com/tee.jpg",
                        new BigDecimal("39.99"), breakdown));
        when(garmentSwapService.listAlternatives(eq(outfitId), eq(itemId), any())).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/outfits/{outfitId}/items/{itemId}/alternatives", outfitId, itemId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateTestAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productId").value(candidateId.toString()))
                .andExpect(jsonPath("$[0].productDisplayName").value("Slim Fit Tee"))
                .andExpect(jsonPath("$[0].basePrice").value(39.99))
                .andExpect(jsonPath("$[0].projectedBreakdown.finalScore").value(0.82));
    }

    @Test
    void swap_missingAuthorizationHeader_returns401() throws Exception {
        String body = """
                {"productId": "%s"}
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/outfits/{outfitId}/items/{itemId}/swap",
                        UUID.randomUUID(), UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void swap_validTokenAndBody_returns200WithMappedOutfitAndDerivesUserIdFromJwtSubject() throws Exception {
        UUID outfitId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID resultOutfitId = UUID.randomUUID();
        UUID resultItemId = UUID.randomUUID();
        CompatibilityScoreBreakdown breakdown = new CompatibilityScoreBreakdown(
                new BigDecimal("0.6"), new BigDecimal("0.65"), new BigDecimal("0.7"),
                new BigDecimal("0.75"), new BigDecimal("0.68"));
        OutfitResponse mockResponse = new OutfitResponse(resultOutfitId, breakdown, new BigDecimal("104.99"),
                List.of(new OutfitItemView(resultItemId, productId, "Slim Fit Tee", "https://example.com/tee.jpg",
                        new BigDecimal("39.99"), GarmentRole.TOP)));
        when(garmentSwapService.swap(eq(outfitId), eq(itemId), eq(productId), any())).thenReturn(mockResponse);

        String body = """
                {"productId": "%s"}
                """.formatted(productId);

        mockMvc.perform(post("/api/v1/outfits/{outfitId}/items/{itemId}/swap", outfitId, itemId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateTestAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outfitId").value(resultOutfitId.toString()))
                .andExpect(jsonPath("$.totalPrice").value(104.99))
                .andExpect(jsonPath("$.items[0].itemId").value(resultItemId.toString()))
                .andExpect(jsonPath("$.items[0].productId").value(productId.toString()))
                .andExpect(jsonPath("$.items[0].slot").value("TOP"));
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
                .build();
        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();

        return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
    }
}