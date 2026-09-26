package com.sewlect.outfit.controller;

import com.sewlect.common.ratelimit.RateLimiter;
import com.sewlect.identity.enums.Role;
import com.sewlect.identity.entity.User;
import com.sewlect.identity.service.UserReferenceQueryService;
import com.sewlect.outfit.dto.OutfitResponse;
import com.sewlect.outfit.service.PromptOutfitGenerationService;
import com.sewlect.outfit.support.PromptRateLimitResolver;
import com.sewlect.outfit.service.PromptRefinementService;
import com.sewlect.support.WebSliceTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
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
@Import(WebSliceTestConfig.class)
@TestPropertySource(properties = {
        WebSliceTestConfig.JWT_SECRET_PROPERTY,
        WebSliceTestConfig.JWT_ACCESS_EXPIRATION_PROPERTY,
        WebSliceTestConfig.JWT_REFRESH_EXPIRATION_PROPERTY,
        WebSliceTestConfig.SUBJECT_HASH_SECRET_PROPERTY
})
class PromptControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PromptOutfitGenerationService promptOutfitGenerationService;

    @MockitoBean
    private PromptRefinementService promptRefinementService;

    @MockitoBean
    private RateLimiter inMemoryRateLimiter;

    @MockitoBean
    private PromptRateLimitResolver promptRateLimitResolver;

    @MockitoBean
    private UserReferenceQueryService userReferenceQueryService;

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
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken(userId))
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
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken(userId))
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
}