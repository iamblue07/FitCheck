package com.fitcheck.outfit.service;

import com.fitcheck.catalog.entity.Product;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class OutfitItemSetHasher {

    private static final String HASH_ALGORITHM = "SHA-256";

    public String hash(List<Product> selected) {
        String sortedIds = selected.stream()
                .map(p -> p.getId().toString())
                .sorted()
                .collect(Collectors.joining(","));
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hashBytes = digest.digest(sortedIds.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(HASH_ALGORITHM + " is not available on this JVM", e);
        }
    }
}