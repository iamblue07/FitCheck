package com.fitcheck.identity.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    void builder_setsInheritedAuditableEntityFieldsAlongsideOwnFields() {
        UUID id = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1);
        LocalDateTime updatedAt = LocalDateTime.now();

        User user = User.builder()
                .id(id)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .email("jane@example.com")
                .passwordHash("hashed-value")
                .role(Role.USER)
                .build();

        assertThat(user.getId()).isEqualTo(id);
        assertThat(user.getCreatedAt()).isEqualTo(createdAt);
        assertThat(user.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(user.getEmail()).isEqualTo("jane@example.com");
        assertThat(user.getPasswordHash()).isEqualTo("hashed-value");
        assertThat(user.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void toString_neverIncludesPasswordHash() {
        User user = User.builder()
                .email("jane@example.com")
                .passwordHash("super-secret-hash-value")
                .role(Role.USER)
                .build();

        String result = user.toString();

        assertThat(result).doesNotContain("super-secret-hash-value");
        assertThat(result).doesNotContain("passwordHash");
        assertThat(result).contains("jane@example.com");
    }
}