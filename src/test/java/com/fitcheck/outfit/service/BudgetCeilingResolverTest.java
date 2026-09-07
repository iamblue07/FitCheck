package com.fitcheck.outfit.service;

import com.fitcheck.outfit.config.OutfitGenerationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetCeilingResolverTest {

    private BudgetCeilingResolver resolver;

    @BeforeEach
    void setUp() {
        OutfitGenerationProperties properties = new OutfitGenerationProperties(
                50, 500, new BigDecimal("0.10"), 15, 3);
        resolver = new BudgetCeilingResolver(properties);
    }

    @Test
    void resolve_nullBudget_returnsUnlimitedSentinel() {
        assertThat(resolver.resolve(null)).isGreaterThan(new BigDecimal("100000"));
    }

    @Test
    void resolve_realBudget_appliesTolerance() {
        assertThat(resolver.resolve(new BigDecimal("100"))).isEqualByComparingTo(new BigDecimal("110.00"));
    }

    @Test
    void resolveNullable_nullBudget_returnsNull() {
        assertThat(resolver.resolveNullable(null)).isNull();
    }

    @Test
    void resolveNullable_realBudget_appliesTolerance() {
        assertThat(resolver.resolveNullable(new BigDecimal("100"))).isEqualByComparingTo(new BigDecimal("110.00"));
    }

    @Test
    void exceedsBudget_nullCeiling_neverExceeds() {
        assertThat(resolver.exceedsBudget(new BigDecimal("500"), new BigDecimal("10"), new BigDecimal("1000"), null))
                .isFalse();
    }

    @Test
    void exceedsBudget_projectedTotalOverCeiling_returnsTrue() {
        assertThat(resolver.exceedsBudget(
                new BigDecimal("100"), new BigDecimal("40"), new BigDecimal("51"), new BigDecimal("110")))
                .isTrue();
    }

    @Test
    void exceedsBudget_projectedTotalWithinCeiling_returnsFalse() {
        assertThat(resolver.exceedsBudget(
                new BigDecimal("100"), new BigDecimal("40"), new BigDecimal("45"), new BigDecimal("110")))
                .isFalse();
    }
}