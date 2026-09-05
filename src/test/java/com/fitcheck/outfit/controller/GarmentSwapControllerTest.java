package com.fitcheck.outfit.controller;

import com.fitcheck.common.security.JwtConfig;
import com.fitcheck.common.security.RestAccessDeniedHandler;
import com.fitcheck.common.security.RestAuthenticationEntryPoint;
import com.fitcheck.common.security.SecurityConfig;
import com.fitcheck.identity.service.AppUserDetailsService;
import com.fitcheck.outfit.service.GarmentSwapService;
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

@WebMvcTest(GarmentSwapController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({SecurityConfig.class, JwtConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
@TestPropertySource(properties = {
        "jwt.secret=" + GarmentSwapControllerTest.TEST_JWT_SECRET,
        "jwt.access-expiration=900000",
        "jwt.refresh-expiration=604800000"
})
class GarmentSwapControllerTest {

    static final String TEST_JWT_SECRET = "test-secret-key-at-least-32-characters-long-xxxx";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GarmentSwapService garmentSwapService;

    // Required so SecurityConfig's AuthenticationManager bean has a UserDetailsService to build
    // a DaoAuthenticationProvider around at context startup. Never invoked directly — filters
    // are off, so no Authentication is populated and @AuthenticationPrincipal resolves to null,
    // which is fine: validation on the @RequestBody fails before the controller body ever runs.
    @MockitoBean
    private AppUserDetailsService appUserDetailsService;

    @Test
    void swap_missingProductIdField_returns400AndNeverInvokesService() throws Exception {
        UUID outfitId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        String body = """
                {}
                """;

        mockMvc.perform(post("/api/v1/outfits/{outfitId}/items/{itemId}/swap", outfitId, itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(garmentSwapService);
    }

    @Test
    void swap_explicitNullProductId_returns400AndNeverInvokesService() throws Exception {
        UUID outfitId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        String body = """
                {"productId": null}
                """;

        mockMvc.perform(post("/api/v1/outfits/{outfitId}/items/{itemId}/swap", outfitId, itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(garmentSwapService);
    }

    @Test
    void swap_malformedProductIdValue_returns400() throws Exception {
        UUID outfitId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        String body = """
                {"productId": "not-a-uuid"}
                """;

        mockMvc.perform(post("/api/v1/outfits/{outfitId}/items/{itemId}/swap", outfitId, itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(garmentSwapService);
    }
}