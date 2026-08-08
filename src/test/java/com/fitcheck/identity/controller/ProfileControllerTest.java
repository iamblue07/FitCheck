package com.fitcheck.identity.controller;

import com.fitcheck.common.security.JwtConfig;
import com.fitcheck.common.security.RestAccessDeniedHandler;
import com.fitcheck.common.security.RestAuthenticationEntryPoint;
import com.fitcheck.common.security.SecurityConfig;
import com.fitcheck.identity.service.AppUserDetailsService;
import com.fitcheck.identity.service.ProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({SecurityConfig.class, JwtConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
@TestPropertySource(properties = {
        "jwt.secret=" + ProfileControllerTest.TEST_JWT_SECRET,
        "jwt.access-expiration=900000",
        "jwt.refresh-expiration=604800000"
})
class ProfileControllerTest {

    static final String TEST_JWT_SECRET = "test-secret-key-at-least-32-characters-long-xxxx";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfileService profileService;

    // Required so SecurityConfig's AuthenticationManager bean has a UserDetailsService to build
    // a DaoAuthenticationProvider around at context startup. Never invoked directly — filters
    // are off, so no Authentication is populated and @AuthenticationPrincipal resolves to null,
    // which is fine: validation on the @RequestBody fails before the controller body ever runs.
    @MockitoBean
    private AppUserDetailsService appUserDetailsService;

    @Test
    void updateProfile_heightOutOfValidatedRange_returns400() throws Exception {
        String body = """
                {"heightCm": 999}
                """;

        mockMvc.perform(put("/api/v1/users/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void updateStylePreferences_missingStyleTagIdsField_returns400() throws Exception {
        String body = """
                {}
                """;

        mockMvc.perform(put("/api/v1/users/me/style-preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void updateProfile_negativeBudget_returns400() throws Exception {
        String body = """
            {"averageBudgetPerOutfit": -50}
            """;

        mockMvc.perform(put("/api/v1/users/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}