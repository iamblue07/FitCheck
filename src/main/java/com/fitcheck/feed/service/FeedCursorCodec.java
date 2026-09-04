package com.fitcheck.feed.service;

import com.fitcheck.common.exception.BadRequestException;
import com.fitcheck.feed.dto.FeedCursor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

@Component
public class FeedCursorCodec {

    private static final String SEPARATOR = ":";

    public String encode(FeedCursor cursor) {
        String raw = cursor.rankScore().toPlainString() + SEPARATOR + cursor.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public FeedCursor decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separatorIndex = raw.lastIndexOf(SEPARATOR);
            BigDecimal rankScore = new BigDecimal(raw.substring(0, separatorIndex));
            UUID id = UUID.fromString(raw.substring(separatorIndex + 1));
            return new FeedCursor(rankScore, id);
        } catch (RuntimeException e) {
            throw new BadRequestException("Invalid feed cursor");
        }
    }
}