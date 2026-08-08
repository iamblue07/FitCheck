package com.fitcheck.identity.controller;

import com.fitcheck.common.security.JwtConfig;
import com.fitcheck.common.security.RestAccessDeniedHandler;
import com.fitcheck.common.security.RestAuthenticationEntryPoint;
import com.fitcheck.common.security.SecurityConfig;
import com.fitcheck.identity.dto.StyleTagResponse;
import com.fitcheck.identity.dto.UserProfileResponse;
import com.fitcheck.identity.entity.Sex;
import com.fitcheck.identity.service.AppUserDetailsService;
import com.fitcheck.identity.service.ProfileService;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProfileController.class)
@Import({SecurityConfig.class, JwtConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
@TestPropertySource(properties = {
        "jwt.secret=" + ProfileControllerSecurityTest.TEST_JWT_SECRET,
        "jwt.access-expiration=900000",
        "jwt.refresh-expiration=604800000"
})
class ProfileControllerSecurityTest {

    static final String TEST_JWT_SECRET = "test-secret-key-at-least-32-characters-long-xxxx";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfileService profileService;

    @MockitoBean
    private AppUserDetailsService appUserDetailsService;

    @Test
    void getProfile_validToken_returns200WithMappedBody() throws Exception {
        UserProfileResponse mockResponse = new UserProfileResponse(
                LocalDate.of(1998, 4, 12), Sex.FEMALE, BigDecimal.valueOf(170), BigDecimal.valueOf(62),
                BigDecimal.valueOf(24.5), BigDecimal.valueOf(150), "RON",
                List.of(new StyleTagResponse(UUID.randomUUID(), "minimalist")));
        when(profileService.getProfile(any())).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/users/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateTestAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("RON"))
                .andExpect(jsonPath("$.styleTags[0].name").value("minimalist"));
    }

    @Test
    void updateStylePreferences_validToken_returns200WithMappedList() throws Exception {
        UUID tagId = UUID.randomUUID();
        when(profileService.updateStylePreferences(any(), any()))
                .thenReturn(List.of(new StyleTagResponse(tagId, "minimalist")));

        String body = """
                {"styleTagIds": ["%s"]}
                """.formatted(tagId);

        mockMvc.perform(put("/api/v1/users/me/style-preferences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateTestAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("minimalist"));
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