package com.fitcheck.identity.controller;

import com.fitcheck.common.security.JwtConfig;
import com.fitcheck.common.security.RestAccessDeniedHandler;
import com.fitcheck.common.security.RestAuthenticationEntryPoint;
import com.fitcheck.common.security.SecurityConfig;
import com.fitcheck.identity.service.AppUserDetailsService;
import com.fitcheck.identity.service.AuthService;
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
@TestPropertySource(properties = {
        "jwt.secret=" + AuthControllerSecurityTest.TEST_JWT_SECRET,
        "jwt.access-expiration=900000",
        "jwt.refresh-expiration=604800000"
})
class AuthControllerSecurityTest {

    static final String TEST_JWT_SECRET = "test-secret-key-at-least-32-characters-long-xxxx";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    // Required so SecurityConfig's AuthenticationManager bean has a UserDetailsService to build
    // a DaoAuthenticationProvider around at context startup. Never invoked directly by either
    // test below — neither one exercises the login path, only per-request JWT validation.
    @MockitoBean
    private AppUserDetailsService appUserDetailsService;

    @Test
    void protectedEndpoint_missingAuthorizationHeader_returns401InErrorResponseShape() throws Exception {
        mockMvc.perform(get("/api/v1/anything"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void protectedEndpoint_validToken_passesSecurityAndReaches404ForUnmappedRoute() throws Exception {
        String validToken = generateTestAccessToken();

        mockMvc.perform(get("/api/v1/anything").header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken))
                .andDo(print()).andExpect(status().isNotFound());
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