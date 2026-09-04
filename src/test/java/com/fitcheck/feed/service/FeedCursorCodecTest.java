package com.fitcheck.feed.service;

import com.fitcheck.common.exception.BadRequestException;
import com.fitcheck.feed.dto.FeedCursor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeedCursorCodecTest {

    private final FeedCursorCodec codec = new FeedCursorCodec();

    @Test
    void encodeThenDecode_roundTripsExactly() {
        FeedCursor original = new FeedCursor(new BigDecimal("0.8765"), UUID.randomUUID());

        FeedCursor decoded = codec.decode(codec.encode(original));

        assertThat(decoded.rankScore()).isEqualByComparingTo(original.rankScore());
        assertThat(decoded.id()).isEqualTo(original.id());
    }

    @Test
    void encodeThenDecode_roundTripsForZeroRankScore() {
        FeedCursor original = new FeedCursor(BigDecimal.ZERO, UUID.randomUUID());

        FeedCursor decoded = codec.decode(codec.encode(original));

        assertThat(decoded.rankScore()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(decoded.id()).isEqualTo(original.id());
    }

    @Test
    void encodeThenDecode_roundTripsForHighPrecisionRankScore() {
        // more decimal places than the DB column's numeric(6,4) would ever produce -
        // codec itself shouldn't silently truncate
        FeedCursor original = new FeedCursor(new BigDecimal("1.123456789"), UUID.randomUUID());

        FeedCursor decoded = codec.decode(codec.encode(original));

        assertThat(decoded.rankScore()).isEqualByComparingTo(original.rankScore());
    }

    @Test
    void encode_isUrlSafe_noPaddingOrPlusOrSlashCharacters() {
        FeedCursor cursor = new FeedCursor(new BigDecimal("0.9999"), UUID.randomUUID());

        String encoded = codec.encode(cursor);

        assertThat(encoded).doesNotContain("=", "+", "/");
    }

    @Test
    void decode_validBase64ButNoSeparator_throwsBadRequest() {
        String noSeparator = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("nocolonhere".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> codec.decode(noSeparator))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void decode_validBase64ButNonNumericRankScore_throwsBadRequest() {
        String raw = "notANumber:" + UUID.randomUUID();
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> codec.decode(encoded))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void decode_validBase64ButNonUuidId_throwsBadRequest() {
        String raw = "0.5000:not-a-real-uuid";
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> codec.decode(encoded))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void decode_extraColonsInPayload_splitsOnLastOneAndStillFailsCleanly() {
        // rankScore portion becomes "1.23:fake" once split on the LAST colon -
        // must be rejected, not silently mis-parsed
        String raw = "1.23:fake:" + UUID.randomUUID();
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> codec.decode(encoded))
                .isInstanceOf(BadRequestException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-base64-at-all-%%%", "====", " "})
    void decode_garbageInput_alwaysThrowsBadRequestNeverAnUncheckedException(String garbage) {
        assertThatThrownBy(() -> codec.decode(garbage))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void decode_tamperedValidCursor_throwsBadRequestRatherThanSilentlyAcceptingCorruption() {
        String validCursor = codec.encode(new FeedCursor(new BigDecimal("0.7500"), UUID.randomUUID()));
        // flip one character in the middle - simulates a client passing back a
        // corrupted or hand-edited cursor rather than the exact opaque token we gave them
        char[] chars = validCursor.toCharArray();
        int mid = chars.length / 2;
        chars[mid] = chars[mid] == 'A' ? 'B' : 'A';
        String tampered = new String(chars);

        // Either it still happens to decode to *some* value (acceptable - opaque
        // tokens aren't tamper-proof by design) or it throws BadRequestException.
        // What it must never do is throw anything else (e.g. NPE, StringIndexOutOfBounds).
        try {
            codec.decode(tampered);
        } catch (Exception e) {
            assertThat(e).isInstanceOf(BadRequestException.class);
        }
    }

    @Test
    void decode_null_throwsBadRequestNotNullPointerException() {
        assertThatThrownBy(() -> codec.decode(null))
                .isInstanceOf(BadRequestException.class);
    }
}