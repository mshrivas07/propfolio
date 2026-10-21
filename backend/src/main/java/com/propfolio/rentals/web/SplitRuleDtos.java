package com.propfolio.rentals.web;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import com.propfolio.rentals.ShareType;

/**
 * Request and response bodies for split rules.
 */
public final class SplitRuleDtos {

    private SplitRuleDtos() {
    }

    /** Creates the rule for this unit + utility account, or replaces the existing one. */
    public record SplitRuleRequest(
            @NotNull(message = "Choose a unit") UUID unitId,
            @NotNull(message = "Choose a utility account") UUID utilityAccountId,
            @NotNull(message = "Choose percentage or flat amount") ShareType shareType,
            @NotNull(message = "Enter a value")
            @DecimalMin(value = "0", inclusive = false, message = "Enter a value greater than 0")
            @Digits(integer = 8, fraction = 4, message = "Use at most 4 decimal places")
            BigDecimal shareValue) {
    }

    public record SplitRuleResponse(
            UUID id,
            UUID unitId,
            UUID utilityAccountId,
            ShareType shareType,
            BigDecimal shareValue) {
    }
}
