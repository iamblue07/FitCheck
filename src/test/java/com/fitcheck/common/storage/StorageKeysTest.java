package com.fitcheck.common.storage;

import com.fitcheck.common.exception.InvalidPhotoTypeException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageKeysTest {

    @Test
    void bodyPhotoKey_matchesExpectedFormat() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        String key = StorageKeys.bodyPhotoKey(userId, "front");

        assertThat(key).isEqualTo("body-photos/11111111-1111-1111-1111-111111111111/front.jpg");
    }

    @Test
    void bodyPhotoKey_invalidPhotoType_throwsInvalidPhotoTypeException() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> StorageKeys.bodyPhotoKey(userId, "side"))
                .isInstanceOf(InvalidPhotoTypeException.class)
                .hasMessageContaining("side");
    }

    @Test
    void tryonResultKey_matchesExpectedFormat() {
        UUID requestId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        String key = StorageKeys.tryonResultKey(requestId);

        assertThat(key).isEqualTo("tryon-results/22222222-2222-2222-2222-222222222222.jpg");
    }
}