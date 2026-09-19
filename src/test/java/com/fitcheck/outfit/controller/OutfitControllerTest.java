package com.fitcheck.outfit.controller;

import com.fitcheck.common.exception.ResourceNotFoundException;
import com.fitcheck.common.config.CommonBeansConfig;
import com.fitcheck.common.exception.support.ErrorResponseFactory;
import com.fitcheck.common.ratelimit.InMemoryRateLimiter;
import com.fitcheck.common.security.config.JwtConfig;
import com.fitcheck.common.security.config.SecurityConfig;
import com.fitcheck.common.security.handler.RestAccessDeniedHandler;
import com.fitcheck.common.security.handler.RestAuthenticationEntryPoint;
import com.fitcheck.common.taxonomy.enums.GarmentRole;
import com.fitcheck.identity.service.AppUserDetailsService;
import com.fitcheck.outfit.domain.CompatibilityScoreBreakdown;
import com.fitcheck.outfit.domain.OutfitItemView;
import com.fitcheck.outfit.dto.OutfitResponse;
import com.fitcheck.outfit.entity.Outfit;
import com.fitcheck.outfit.service.OutfitItemQueryService;
import com.fitcheck.outfit.support.OutfitResponseAssembler;
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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OutfitController.class)
@Import({SecurityConfig.class, JwtConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        ErrorResponseFactory.class, CommonBeansConfig.class})
@TestPropertySource(properties = {
        "jwt.secret=" + OutfitControllerTest.TEST_JWT_SECRET,
        "jwt.access-expiration=900000",
        "jwt.refresh-expiration=604800000"
})
class OutfitControllerTest {

    static final String TEST_JWT_SECRET = "test-secret-key-at-least-32-characters-long-xxxx";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InMemoryRateLimiter inMemoryRateLimiter;

    @MockitoBean
    private OutfitItemQueryService outfitItemQueryService;

    @MockitoBean
    private OutfitResponseAssembler outfitResponseAssembler;

    @MockitoBean
    private AppUserDetailsService appUserDetailsService;

    @Test
    void getOutfit_existingId_returnsOutfitResponse() throws Exception {
        UUID outfitId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Outfit outfit = Outfit.builder().id(outfitId).build();
        OutfitResponse mockResponse = new OutfitResponse(outfitId,
                new CompatibilityScoreBreakdown(new BigDecimal("0.9"), new BigDecimal("0.8"),
                        new BigDecimal("0.85"), new BigDecimal("0.7"), new BigDecimal("0.82")),
                new BigDecimal("129.99"),
                List.of(new OutfitItemView(itemId, productId, "Slim Fit Tee", "https://example.com/tee.jpg",
                        new BigDecimal("39.99"), GarmentRole.TOP)));

        when(outfitItemQueryService.getById(outfitId)).thenReturn(outfit);
        when(outfitResponseAssembler.toResponse(outfit)).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/outfits/{outfitId}", outfitId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateTestAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outfitId").value(outfitId.toString()))
                .andExpect(jsonPath("$.totalPrice").value(129.99))
                .andExpect(jsonPath("$.compatibilityBreakdown.finalScore").value(0.82))
                .andExpect(jsonPath("$.items[0].itemId").value(itemId.toString()))
                .andExpect(jsonPath("$.items[0].slot").value("TOP"));
    }

    @Test
    void getOutfit_nonExistentId_returns404() throws Exception {
        UUID outfitId = UUID.randomUUID();
        when(outfitItemQueryService.getById(outfitId))
                .thenThrow(new ResourceNotFoundException("Outfit not found: " + outfitId));

        mockMvc.perform(get("/api/v1/outfits/{outfitId}", outfitId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateTestAccessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        verifyNoInteractions(outfitResponseAssembler);
    }

    @Test
    void getOutfit_malformedUuidInPath_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/outfits/{outfitId}", "not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateTestAccessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(outfitItemQueryService);
        verifyNoInteractions(outfitResponseAssembler);
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