package com.fitcheck.outfit.support;

import com.fitcheck.outfit.properties.OutfitGenerationProperties;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@AllArgsConstructor
public class BudgetCeilingResolver {

    private static final BigDecimal UNLIMITED_PRICE_CEILING = new BigDecimal("1000000");

    private final OutfitGenerationProperties properties;

    public BigDecimal resolve(BigDecimal averageBudgetPerOutfit) {
        if (averageBudgetPerOutfit == null) {
            return UNLIMITED_PRICE_CEILING;
        }
        return averageBudgetPerOutfit.multiply(BigDecimal.ONE.add(properties.budgetTolerance()));
    }

    public BigDecimal resolveNullable(BigDecimal averageBudgetPerOutfit) {
        if (averageBudgetPerOutfit == null) {
            return null;
        }
        return averageBudgetPerOutfit.multiply(BigDecimal.ONE.add(properties.budgetTolerance()));
    }

    public boolean exceedsBudget(BigDecimal outfitTotal, BigDecimal outgoingPrice, BigDecimal incomingPrice,
                                 BigDecimal ceiling) {
        if (ceiling == null) {
            return false;
        }
        BigDecimal projectedTotal = outfitTotal.subtract(outgoingPrice).add(incomingPrice);
        return projectedTotal.compareTo(ceiling) > 0;
    }
}