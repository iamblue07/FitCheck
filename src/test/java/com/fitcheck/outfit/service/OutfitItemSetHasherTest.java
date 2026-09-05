package com.fitcheck.outfit.service;

import com.fitcheck.catalog.entity.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutfitItemSetHasherTest {

    private OutfitItemSetHasher hasher;

    @BeforeEach
    void setUp() {
        hasher = new OutfitItemSetHasher();
    }

    @Test
    void hash_isDeterministic_sameInputProducesSameHash() {
        Product a = productWithId(UUID.randomUUID());
        Product b = productWithId(UUID.randomUUID());
        List<Product> selected = List.of(a, b);

        assertThat(hasher.hash(selected)).isEqualTo(hasher.hash(selected));
    }

    @Test
    void hash_orderIndependent_sameProductsDifferentListOrderProduceSameHash() {
        Product a = productWithId(UUID.randomUUID());
        Product b = productWithId(UUID.randomUUID());

        String hashForward = hasher.hash(List.of(a, b));
        String hashReversed = hasher.hash(List.of(b, a));

        assertThat(hashForward).isEqualTo(hashReversed);
    }

    @Test
    void hash_differentProductSets_produceDifferentHashes() {
        Product a = productWithId(UUID.randomUUID());
        Product b = productWithId(UUID.randomUUID());
        Product c = productWithId(UUID.randomUUID());

        String hashAb = hasher.hash(List.of(a, b));
        String hashAc = hasher.hash(List.of(a, c));

        assertThat(hashAb).isNotEqualTo(hashAc);
    }

    @Test
    void hash_singleDifferingProductInOtherwiseIdenticalSet_producesDifferentHash() {
        Product a = productWithId(UUID.randomUUID());
        Product b = productWithId(UUID.randomUUID());
        Product bReplacement = productWithId(UUID.randomUUID());

        String original = hasher.hash(List.of(a, b));
        String swapped = hasher.hash(List.of(a, bReplacement));

        assertThat(original).isNotEqualTo(swapped);
    }

    @Test
    void hash_returnsLowercaseHexEncodedSha256() {
        Product a = productWithId(UUID.randomUUID());
        Product b = productWithId(UUID.randomUUID());

        String hash = hasher.hash(List.of(a, b));

        assertThat(hash).hasSize(64); // SHA-256 -> 32 bytes -> 64 hex chars
        assertThat(hash).matches("[0-9a-f]{64}");
    }

    private Product productWithId(UUID id) {
        return Product.builder().id(id).build();
    }
}