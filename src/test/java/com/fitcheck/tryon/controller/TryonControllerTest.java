package com.fitcheck.tryon.controller;

import com.fitcheck.common.security.config.JwtConfig;
import com.fitcheck.common.security.config.SecurityConfig;
import com.fitcheck.common.security.handler.RestAccessDeniedHandler;
import com.fitcheck.common.security.handler.RestAuthenticationEntryPoint;
import com.fitcheck.identity.service.AppUserDetailsService;
import com.fitcheck.tryon.service.TryonRequestService;
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TryonController.class)
@Import({SecurityConfig.class, JwtConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
@TestPropertySource(properties = {
        "jwt.secret=" + TryonControllerTest.TEST_JWT_SECRET,
        "jwt.access-expiration=900000",
        "jwt.refresh-expiration=604800000"
})
class TryonControllerTest {

    static final String TEST_JWT_SECRET = "test-secret-key-at-least-32-characters-long-xxxx";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TryonRequestService tryonRequestService;

    @MockitoBean
    private AppUserDetailsService appUserDetailsService;

    @Test
    void submit_missingOutfitIdField_returns400AndNeverInvokesService() throws Exception {
        mockMvc.perform(post("/api/v1/tryon")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateTestAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(tryonRequestService);
    }

    @Test
    void submit_nullOutfitIdValue_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/tryon")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateTestAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outfitId\": null}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(tryonRequestService);
    }

    @Test
    void submit_malformedOutfitIdNotAUuid_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/tryon")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateTestAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outfitId\": \"not-a-uuid\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(tryonRequestService);
    }

    @Test
    void submit_malformedJsonBody_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/tryon")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateTestAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not json at all"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(tryonRequestService);
    }

    @Test
    void submit_missingBodyEntirely_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/tryon")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateTestAccessToken())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(tryonRequestService);
    }

    @Test
    void getStatus_requestIdNotAUuid_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/tryon/{requestId}", "not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateTestAccessToken()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(tryonRequestService);
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