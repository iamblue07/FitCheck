package com.fitcheck.outfit.service;

import com.fitcheck.identity.entity.Sex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutfitGenderFilterResolverTest {

    private OutfitGenderFilterResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new OutfitGenderFilterResolver();
    }

    @Test
    void allowedGenders_male_resolvesToMenAndUnisex() {
        assertThat(resolver.allowedGenders(Sex.MALE)).containsExactlyInAnyOrder("Men", "Unisex");
    }

    @Test
    void allowedGenders_female_resolvesToWomenAndUnisex() {
        assertThat(resolver.allowedGenders(Sex.FEMALE)).containsExactlyInAnyOrder("Women", "Unisex");
    }

    @Test
    void allowedGenders_other_resolvesToAllFiveRawGenderValues() {
        assertThat(resolver.allowedGenders(Sex.OTHER))
                .containsExactlyInAnyOrder("Men", "Women", "Boys", "Girls", "Unisex");
    }

    @Test
    void allowedGenders_nullSex_resolvesToAllFiveRawGenderValues() {
        assertThat(resolver.allowedGenders(null))
                .containsExactlyInAnyOrder("Men", "Women", "Boys", "Girls", "Unisex");
    }

    @Test
    void allowedGenders_maleAndFemale_neverIncludeBoysOrGirls() {
        assertThat(resolver.allowedGenders(Sex.MALE)).doesNotContain("Boys", "Girls");
        assertThat(resolver.allowedGenders(Sex.FEMALE)).doesNotContain("Boys", "Girls");
    }
}