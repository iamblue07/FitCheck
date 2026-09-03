package com.fitcheck.common.taxonomy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GarmentRoleResolverTest {

    private final GarmentRoleResolver resolver = new GarmentRoleResolver();

    @Test
    void resolve_topArticleType_returnsTop() {
        assertThat(resolver.resolve("Tshirts")).contains(GarmentRole.TOP);
    }

    @Test
    void resolve_bottomArticleType_returnsBottom() {
        assertThat(resolver.resolve("Jeans")).contains(GarmentRole.BOTTOM);
    }

    @Test
    void resolve_fullBodyArticleType_returnsFullBody() {
        assertThat(resolver.resolve("Dresses")).contains(GarmentRole.FULL_BODY);
    }

    @Test
    void resolve_footwearArticleType_returnsFootwear() {
        assertThat(resolver.resolve("Casual Shoes")).contains(GarmentRole.FOOTWEAR);
    }

    @Test
    void resolve_outerwearArticleType_returnsOuterwear() {
        assertThat(resolver.resolve("Jackets")).contains(GarmentRole.OUTERWEAR);
    }

    @Test
    void resolve_accessoryArticleType_returnsAccessory() {
        assertThat(resolver.resolve("Wallets")).contains(GarmentRole.ACCESSORY);
    }

    @Test
    void resolve_unmappedArticleType_returnsEmpty() {
        // "Socks" is explicitly one of the ~64 unmapped types, not accidentally omitted
        assertThat(resolver.resolve("Socks")).isEmpty();
    }

    @Test
    void resolve_nullArticleType_returnsEmptyRatherThanThrowing() {
        assertThat(resolver.resolve(null)).isEmpty();
    }

    @Test
    void resolve_isExactMatchOnly_doesNotNormalizeCasing() {
        // Deliberately unlike the color resolver: article_type is a controlled dataset field,
        // so exact match is correct here and case-folding would mask a real data problem.
        assertThat(resolver.resolve("tshirts")).isEmpty();
    }
}