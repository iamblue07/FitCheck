package com.fitcheck.identity.dto;

import com.fitcheck.identity.entity.Sex;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UserProfileUpdateRequest(

        LocalDate birthDate,

        Sex sex,

        @DecimalMin(value = "0", inclusive = false)
        @DecimalMax(value = "300")
        BigDecimal heightCm,

        @DecimalMin(value = "0", inclusive = false)
        @DecimalMax(value = "500")
        BigDecimal weightKg,

        @DecimalMin(value = "0", inclusive = false)
        @DecimalMax(value = "50")
        BigDecimal footLengthCm,

        @PositiveOrZero
        BigDecimal averageBudgetPerOutfit,

        String currency
) {
}
