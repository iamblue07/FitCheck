package com.fitcheck.identity.service;

import com.fitcheck.common.security.JwtProperties;
import com.fitcheck.identity.entity.Role;
import com.fitcheck.identity.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class JwtServiceTest {

    private static final String TEST_SECRET = "test-secret-key-at-least-32-characters-long-xxxx";

    private JwtDecoder jwtDecoder;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        SecretKey secretKey = new SecretKeySpec(TEST_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder jwtEncoder = NimbusJwtEncoder.withSecretKey(secretKey).build();
        jwtDecoder = NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();

        JwtProperties jwtProperties = new JwtProperties(TEST_SECRET, Duration.ofMinutes(15), Duration.ofDays(7));
        jwtService = new JwtService(jwtEncoder, jwtProperties);
    }

    private User buildUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("jane@example.com")
                .passwordHash("irrelevant-for-this-test")
                .role(Role.ADMIN)
                .build();
    }

    @Test
    void generateAccessToken_containsSubEmailAndRoleClaims() {
        User user = buildUser();

        String token = jwtService.generateAccessToken(user);
        Jwt decoded = jwtDecoder.decode(token);

        assertThat(decoded.getSubject()).isEqualTo(user.getId().toString());
        assertThat(decoded.getClaimAsString("email")).isEqualTo("jane@example.com");
        assertThat(decoded.getClaimAsString("role")).isEqualTo("ADMIN");
    }

    @Test
    void generateAccessToken_expiresInConfiguredFifteenMinutes() {
        User user = buildUser();
        Instant beforeGeneration = Instant.now();

        String token = jwtService.generateAccessToken(user);
        Jwt decoded = jwtDecoder.decode(token);

        Instant expectedExpiry = beforeGeneration.plus(Duration.ofMinutes(15));
        assertThat(decoded.getExpiresAt()).isCloseTo(expectedExpiry, within(2, ChronoUnit.SECONDS));
    }

    @Test
    void generateRefreshToken_producesSufficientlyRandomValueOfExpectedLength() {
        String token1 = jwtService.generateRefreshToken();
        String token2 = jwtService.generateRefreshToken();

        assertThat(token1).isNotEqualTo(token2);
        assertThat(token1).matches("^[A-Za-z0-9_-]+$");
        assertThat(Base64.getUrlDecoder().decode(token1)).hasSize(32);
    }

    @Test
    void hashToken_isDeterministic() {
        String rawToken = "some-raw-refresh-token-value";

        String hash1 = jwtService.hashToken(rawToken);
        String hash2 = jwtService.hashToken(rawToken);

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).matches("^[0-9a-f]{64}$");
    }
}