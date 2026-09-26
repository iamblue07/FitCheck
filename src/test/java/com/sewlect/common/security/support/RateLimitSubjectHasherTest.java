package com.sewlect.common.security.support;

import com.sewlect.common.security.properties.AuthRateLimitProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitSubjectHasherTest {

    private final RateLimitSubjectHasher hasher = new RateLimitSubjectHasher(
            new AuthRateLimitProperties(20, 10, Duration.ofMinutes(15), "test-rate-limit-secret-at-least-32-chars"));

    @Test
    void hash_sameInput_producesTheSameHash() {
        assertThat(hasher.hash("jane@example.com")).isEqualTo(hasher.hash("jane@example.com"));
    }

    @Test
    void hash_differentInputs_produceDifferentHashes() {
        assertThat(hasher.hash("jane@example.com")).isNotEqualTo(hasher.hash("john@example.com"));
        assertThat(hasher.hash("203.0.113.7")).isNotEqualTo(hasher.hash("203.0.113.8"));
    }

    @Test
    void hash_outputIs64LowercaseHexCharacters() {
        assertThat(hasher.hash("jane@example.com")).matches("[0-9a-f]{64}");
    }

    @Test
    void hash_outputNeverContainsTheInput() {
        List<String> subjects = List.of("jane@example.com", "203.0.113.7", UUID.randomUUID().toString());

        for (String subject : subjects) {
            assertThat(hasher.hash(subject)).doesNotContain(subject);
        }
    }
}