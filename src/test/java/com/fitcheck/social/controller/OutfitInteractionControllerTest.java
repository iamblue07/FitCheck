package com.fitcheck.social.controller;

import com.fitcheck.common.config.CommonBeansConfig;
import com.fitcheck.common.exception.support.ErrorResponseFactory;
import com.fitcheck.common.ratelimit.InMemoryRateLimiter;
import com.fitcheck.common.security.config.JwtConfig;
import com.fitcheck.common.security.config.SecurityConfig;
import com.fitcheck.common.security.handler.RestAccessDeniedHandler;
import com.fitcheck.common.security.handler.RestAuthenticationEntryPoint;
import com.fitcheck.identity.service.AppUserDetailsService;
import com.fitcheck.social.dto.InteractionStateResponse;
import com.fitcheck.social.dto.SavedOutfitsResponse;
import com.fitcheck.social.enums.InteractionType;
import com.fitcheck.social.service.OutfitInteractionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
        "jwt.secret=" + OutfitInteractionControllerTest.TEST_JWT_SECRET,
        "jwt.access-expiration=900000",
        "jwt.refresh-expiration=604800000"
})
class OutfitInteractionControllerTest {

    static final String TEST_JWT_SECRET = "test-secret-key-at-least-32-characters-long-xxxx";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OutfitInteractionService outfitInteractionService;

    @MockitoBean
    private AppUserDetailsService appUserDetailsService;

    @MockitoBean
    private InMemoryRateLimiter inMemoryRateLimiter;

    @Test
    void like_returnsInteractionStateResponseFromService() throws Exception {
        UUID outfitId = UUID.randomUUID();
        when(outfitInteractionService.activate(any(), eq(outfitId), eq(InteractionType.LIKE)))
                .thenReturn(new InteractionStateResponse(true));

        mockMvc.perform(post("/api/v1/outfits/{outfitId}/like", outfitId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateTestAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void unlike_returnsInteractionStateResponseFromService() throws Exception {
        UUID outfitId = UUID.randomUUID();
        when(outfitInteractionService.deactivate(any(), eq(outfitId), eq(InteractionType.LIKE)))
                .thenReturn(new InteractionStateResponse(false));

        mockMvc.perform(delete("/api/v1/outfits/{outfitId}/like", outfitId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateTestAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void save_returnsInteractionStateResponseFromService() throws Exception {
        UUID outfitId = UUID.randomUUID();
        when(outfitInteractionService.activate(any(), eq(outfitId), eq(InteractionType.SAVE)))
                .thenReturn(new InteractionStateResponse(true));

        mockMvc.perform(post("/api/v1/outfits/{outfitId}/save", outfitId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateTestAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void unsave_returnsInteractionStateResponseFromService() throws Exception {
        UUID outfitId = UUID.randomUUID();
        when(outfitInteractionService.deactivate(any(), eq(outfitId), eq(InteractionType.SAVE)))
                .thenReturn(new InteractionStateResponse(false));

        mockMvc.perform(delete("/api/v1/outfits/{outfitId}/save", outfitId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateTestAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void like_malformedOutfitIdInPath_returns400NotServerError() throws Exception {
        mockMvc.perform(post("/api/v1/outfits/{outfitId}/like", "not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateTestAccessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(outfitInteractionService);
    }

    @Test
    void listSaved_defaultPageAndSize_usesPageZeroSizeTwenty() throws Exception {
        stubEmptySavedPage();

        mockMvc.perform(get("/api/v1/outfits/saved")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateTestAccessToken()))
                .andExpect(status().isOk());

        assertPageable(0, 20);
    }

    @Test
    void listSaved_sizeZero_clampsToDefaultTwenty() throws Exception {
        stubEmptySavedPage();

        mockMvc.perform(get("/api/v1/outfits/saved").param("size", "0")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateTestAccessToken()))
                .andExpect(status().isOk());

        assertPageable(0, 20);
    }

    @Test
    void listSaved_negativeSize_clampsToDefaultTwenty() throws Exception {
        stubEmptySavedPage();

        mockMvc.perform(get("/api/v1/outfits/saved").param("size", "-5")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateTestAccessToken()))
                .andExpect(status().isOk());

        assertPageable(0, 20);
    }

    @Test
    void listSaved_sizeAboveMax_clampsToOneHundred() throws Exception {
        stubEmptySavedPage();

        mockMvc.perform(get("/api/v1/outfits/saved").param("size", "5000")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateTestAccessToken()))
                .andExpect(status().isOk());

        assertPageable(0, 100);
    }

    @Test
    void listSaved_sizeExactlyAtMax_staysOneHundredUnclamped() throws Exception {
        stubEmptySavedPage();

        mockMvc.perform(get("/api/v1/outfits/saved").param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateTestAccessToken()))
                .andExpect(status().isOk());

        assertPageable(0, 100);
    }

    @Test
    void listSaved_negativePage_clampsToZero() throws Exception {
        stubEmptySavedPage();

        mockMvc.perform(get("/api/v1/outfits/saved").param("page", "-3")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateTestAccessToken()))
                .andExpect(status().isOk());

        assertPageable(0, 20);
    }

    @Test
    void listInteractedOutfitIds_missingTypeParam_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/outfits/interactions/mine")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateTestAccessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(outfitInteractionService);
    }

    @Test
    void listInteractedOutfitIds_invalidTypeValue_returns400NotServerError() throws Exception {
        mockMvc.perform(get("/api/v1/outfits/interactions/mine").param("type", "SHARE_NOW")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateTestAccessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(get("/api/v1/outfits/interactions/mine").param("type", "like")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateTestAccessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(outfitInteractionService);
    }

    @Test
    void listInteractedOutfitIds_validType_returnsSetFromService() throws Exception {
        UUID likedOutfitId = UUID.randomUUID();
        when(outfitInteractionService.listInteractedOutfitIds(any(), eq(InteractionType.LIKE)))
                .thenReturn(Set.of(likedOutfitId));

        mockMvc.perform(get("/api/v1/outfits/interactions/mine").param("type", "LIKE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateTestAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value(likedOutfitId.toString()));
    }

    private void stubEmptySavedPage() {
        when(outfitInteractionService.listSaved(any(), any()))
                .thenReturn(new SavedOutfitsResponse(List.of(), 0, 20, 0L, 0));
    }

    private void assertPageable(int expectedPage, int expectedSize) {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(outfitInteractionService).listSaved(any(), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(expectedPage);
        assertThat(captor.getValue().getPageSize()).isEqualTo(expectedSize);
    }

    private String generateTestAccessToken() {
        SecretKey secretKey = new SecretKeySpec(TEST_JWT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder jwtEncoder = NimbusJwtEncoder.withSecretKey(secretKey).build();

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(UUID.randomUUID().toString())
                .issuer("https://fitcheck.local")
                .audience(List.of("fitcheck-api"))
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(15)))
                .claim("role", "USER")
                .build();
        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();

        return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
    }
}