package com.fitcheck.catalog.controller;

import com.fitcheck.catalog.entity.Product;
import com.fitcheck.catalog.service.CatalogEnrichmentService;
import com.fitcheck.common.security.JwtConfig;
import com.fitcheck.common.security.RestAccessDeniedHandler;
import com.fitcheck.common.security.RestAuthenticationEntryPoint;
import com.fitcheck.common.security.SecurityConfig;
import com.fitcheck.identity.service.AppUserDetailsService;
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
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminCatalogController.class)
@Import({SecurityConfig.class, JwtConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
@TestPropertySource(properties = {
        "jwt.secret=" + AdminCatalogControllerTest.TEST_JWT_SECRET,
        "jwt.access-expiration=900000",
        "jwt.refresh-expiration=604800000",
        "spring.ai.model.chat=ollama"
})
class AdminCatalogControllerTest {

    static final String TEST_JWT_SECRET = "test-secret-key-at-least-32-characters-long-xxxx";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatalogEnrichmentService catalogEnrichmentService;

    @MockitoBean
    private AppUserDetailsService appUserDetailsService;

    @Test
    void enrichNext_success_returns200WithEnrichedTrue() throws Exception {
        UUID productId = UUID.randomUUID();
        Product product = Product.builder().id(productId).productDisplayName("Blue Cotton Shirt").build();
        when(catalogEnrichmentService.enrichNext()).thenReturn(Optional.of(product));

        mockMvc.perform(post("/admin/catalog/enrich-next")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateToken("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enriched").value(true))
                .andExpect(jsonPath("$.productId").value(productId.toString()))
                .andExpect(jsonPath("$.productDisplayName").value("Blue Cotton Shirt"));
    }

    @Test
    void enrichNext_nothingLeftToEnrich_returns200WithEnrichedFalse() throws Exception {
        when(catalogEnrichmentService.enrichNext()).thenReturn(Optional.empty());

        mockMvc.perform(post("/admin/catalog/enrich-next")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateToken("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enriched").value(false))
                .andExpect(jsonPath("$.productId").doesNotExist())
                .andExpect(jsonPath("$.productDisplayName").doesNotExist());
    }

    @Test
    void enrichNext_nonAdminRole_returns403() throws Exception {
        mockMvc.perform(post("/admin/catalog/enrich-next")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateToken("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void enrichNext_missingToken_returns401() throws Exception {
        mockMvc.perform(post("/admin/catalog/enrich-next"))
                .andExpect(status().isUnauthorized());
    }

    private String generateToken(String role) {
        SecretKey secretKey = new SecretKeySpec(TEST_JWT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder jwtEncoder = NimbusJwtEncoder.withSecretKey(secretKey).build();

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(15)))
                .claim("email", "test@example.com")
                .claim("role", role)
                .build();
        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();

        return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
    }
}