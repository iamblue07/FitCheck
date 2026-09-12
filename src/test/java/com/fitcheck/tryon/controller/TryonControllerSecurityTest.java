package com.fitcheck.tryon.controller;

import com.fitcheck.common.exception.ResourceNotFoundException;
import com.fitcheck.common.security.config.JwtConfig;
import com.fitcheck.common.security.config.SecurityConfig;
import com.fitcheck.common.security.handler.RestAccessDeniedHandler;
import com.fitcheck.common.security.handler.RestAuthenticationEntryPoint;
import com.fitcheck.identity.service.AppUserDetailsService;
import com.fitcheck.tryon.dto.TryonStatusResponse;
import com.fitcheck.tryon.enums.TryonRequestStatus;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TryonController.class)
@Import({SecurityConfig.class, JwtConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
@TestPropertySource(properties = {
        "jwt.secret=" + TryonControllerSecurityTest.TEST_JWT_SECRET,
        "jwt.access-expiration=900000",
        "jwt.refresh-expiration=604800000"
})
class TryonControllerSecurityTest {

    static final String TEST_JWT_SECRET = "test-secret-key-at-least-32-characters-long-xxxx";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TryonRequestService tryonRequestService;

    @MockitoBean
    private AppUserDetailsService appUserDetailsService;

    @Test
    void submit_missingAuthorizationHeader_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/tryon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outfitId\": \"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void submit_validToken_returns202WithPendingStatusBody() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID outfitId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        when(tryonRequestService.submit(any(), any()))
                .thenReturn(new TryonStatusResponse(requestId, TryonRequestStatus.PENDING, null, null));

        mockMvc.perform(post("/api/v1/tryon")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateTestAccessToken(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outfitId\": \"" + outfitId + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(requestId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.resultImageUrl").doesNotExist())
                .andExpect(jsonPath("$.errorMessage").doesNotExist());
    }

    @Test
    void submit_validToken_extractsUserIdFromJwtSubjectAndOutfitIdFromBody() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID outfitId = UUID.randomUUID();
        when(tryonRequestService.submit(any(), any()))
                .thenReturn(new TryonStatusResponse(UUID.randomUUID(), TryonRequestStatus.PENDING, null, null));

        mockMvc.perform(post("/api/v1/tryon")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateTestAccessToken(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"outfitId\": \"" + outfitId + "\"}"));

        verify(tryonRequestService).submit(eq(userId), eq(outfitId));
    }

    @Test
    void getStatus_missingAuthorizationHeader_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/tryon/{requestId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getStatus_validToken_returns200WithCompleteStatusAndResultUrl() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        when(tryonRequestService.getStatus(any(), eq(requestId))).thenReturn(new TryonStatusResponse(
                requestId, TryonRequestStatus.COMPLETE, "https://r2.example.com/result-presigned", null));

        mockMvc.perform(get("/api/v1/tryon/{requestId}", requestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateTestAccessToken(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETE"))
                .andExpect(jsonPath("$.resultImageUrl").value("https://r2.example.com/result-presigned"));
    }

    @Test
    void getStatus_validToken_extractsUserIdFromJwtSubject() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        when(tryonRequestService.getStatus(any(), eq(requestId))).thenReturn(
                new TryonStatusResponse(requestId, TryonRequestStatus.PENDING, null, null));

        mockMvc.perform(get("/api/v1/tryon/{requestId}", requestId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateTestAccessToken(userId)));

        verify(tryonRequestService).getStatus(eq(userId), eq(requestId));
    }

    @Test
    void getStatus_serviceThrowsResourceNotFoundForNonOwnerOrMissingRequest_returns404() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        when(tryonRequestService.getStatus(any(), eq(requestId)))
                .thenThrow(new ResourceNotFoundException("Tryon request not found: " + requestId));

        mockMvc.perform(get("/api/v1/tryon/{requestId}", requestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateTestAccessToken(userId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getStatus_failedRequest_returns200WithErrorMessageAndNullResultUrl() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        when(tryonRequestService.getStatus(any(), eq(requestId))).thenReturn(new TryonStatusResponse(
                requestId, TryonRequestStatus.FAILED, null, "FASHN try-on step exhausted all retries"));

        mockMvc.perform(get("/api/v1/tryon/{requestId}", requestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateTestAccessToken(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorMessage").value("FASHN try-on step exhausted all retries"))
                .andExpect(jsonPath("$.resultImageUrl").doesNotExist());
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