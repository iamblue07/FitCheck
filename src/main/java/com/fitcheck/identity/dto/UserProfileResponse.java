package com.fitcheck.identity.dto;

import com.fitcheck.identity.entity.Sex;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record UserProfileResponse(

        LocalDate birthDate,
        Sex sex,
        BigDecimal heightCm,
        BigDecimal weightKg,
        BigDecimal footLengthCm,
        BigDecimal averageBudgetPerOutfit,
        String currency,
        List<StyleTagResponse> styleTags
) {
}
