package com.sewlect.common.ratelimit;

import com.sewlect.common.properties.RateLimitProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

@Component
@ConditionalOnProperty(name = "common.redis.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitSubjectHasher {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public RateLimitSubjectHasher(RateLimitProperties properties) {
        this.key = new SecretKeySpec(properties.subjectHashSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
    }

    public String hash(String subject) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(subject.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(HMAC_ALGORITHM + " is not available on this JVM", e);
        }
    }
}