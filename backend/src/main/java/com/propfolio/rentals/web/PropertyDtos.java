package com.propfolio.rentals.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request and response bodies for /api/properties.
 */
public final class PropertyDtos {

    private PropertyDtos() {
    }

    public record PropertyRequest(
            @NotBlank(message = "Enter a name for the property") @Size(max = 120) String name,
            @Size(max = 200) String addressLine1,
            @Size(max = 200) String addressLine2,
            @Size(max = 100) String city,
            @Size(max = 100) String province,
            @Size(max = 20) String postalCode) {
    }

    public record PropertyResponse(
            UUID id,
            String name,
            String addressLine1,
            String addressLine2,
            String city,
            String province,
            String postalCode,
            long unitCount,
            long utilityAccountCount) {
    }

    /** Everything the property detail screen needs, in one call. */
    public record PropertyDetailResponse(
            PropertyResponse property,
            List<UnitDtos.UnitResponse> units,
            List<UtilityAccountDtos.UtilityAccountResponse> utilityAccounts,
            List<SplitRuleDtos.SplitRuleResponse> splitRules,
            List<AccountPercentTotal> percentTotals) {
    }

    /** Sum of PERCENT rules on one utility account; the landlord pays the rest. */
    public record AccountPercentTotal(UUID utilityAccountId, BigDecimal percentTotal) {
    }
}
