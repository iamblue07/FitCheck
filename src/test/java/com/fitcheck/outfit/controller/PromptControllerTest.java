package com.fitcheck.outfit.controller;

import com.fitcheck.common.ratelimit.InMemoryRateLimiter;
import com.fitcheck.common.security.JwtConfig;
import com.fitcheck.common.security.RestAccessDeniedHandler;
import com.fitcheck.common.security.RestAuthenticationEntryPoint;
import com.fitcheck.common.security.SecurityConfig;
import com.fitcheck.identity.service.AppUserDetailsService;
import com.fitcheck.identity.service.UserReferenceQueryService;
import com.fitcheck.outfit.service.PromptOutfitGenerationService;
import com.fitcheck.outfit.service.PromptRateLimitResolver;
import com.fitcheck.outfit.service.PromptRefinementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PromptController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({SecurityConfig.class, JwtConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
@TestPropertySource(properties = {
        "jwt.secret=" + PromptControllerTest.TEST_JWT_SECRET,
        "jwt.access-expiration=900000",
        "jwt.refresh-expiration=604800000"
})
class PromptControllerTest {

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
    void generate_blankPrompt_returns400AndNeverInvokesService() throws Exception {
        String body = """
                {"prompt": ""}
                """;

        mockMvc.perform(post("/api/v1/outfits/prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(promptOutfitGenerationService);
    }

    @Test
    void generate_missingPromptField_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/outfits/prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(promptOutfitGenerationService);
    }

    @Test
    void refine_blankPrompt_returns400AndNeverInvokesService() throws Exception {
        UUID outfitId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        String body = """
                {"prompt": ""}
                """;

        mockMvc.perform(post("/api/v1/outfits/{outfitId}/items/{itemId}/refine", outfitId, itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(promptRefinementService);
    }
}