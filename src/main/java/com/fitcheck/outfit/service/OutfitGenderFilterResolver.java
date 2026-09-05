package com.fitcheck.outfit.service;

import com.fitcheck.identity.entity.Sex;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class OutfitGenderFilterResolver {

    private static final Set<String> ALL_GENDERS = Set.of("Men", "Women", "Boys", "Girls", "Unisex");
    private static final Map<Sex, Set<String>> GENDER_FILTERS = Map.of(
            Sex.MALE, Set.of("Men", "Unisex"),
            Sex.FEMALE, Set.of("Women", "Unisex"),
            Sex.OTHER, ALL_GENDERS
    );

    public Set<String> allowedGenders(Sex sex) {
        return sex == null ? ALL_GENDERS : GENDER_FILTERS.get(sex);
    }
}