package com.propfolio.rentals.web;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.propfolio.rentals.UtilityType;

/**
 * Request and response bodies for utility accounts.
 */
public final class UtilityAccountDtos {

    private UtilityAccountDtos() {
    }

    /**
     * @param equalPaymentPlan the account is on an equal/budget payment plan, so tenants pay a share of
     *                         the plan payment (the bill's amount due); null means false
     */
    public record UtilityAccountRequest(
            @NotNull(message = "Choose the type of utility") UtilityType utilityType,
            @NotBlank(message = "Enter the provider name") @Size(max = 120) String providerName,
            @Size(max = 60) String accountNumber,
            Boolean equalPaymentPlan) {

        /** Without the plan flag (not on a plan). */
        public UtilityAccountRequest(UtilityType utilityType, String providerName, String accountNumber) {
            this(utilityType, providerName, accountNumber, false);
        }

        public boolean onPlan() {
            return Boolean.TRUE.equals(equalPaymentPlan);
        }
    }

    public record UtilityAccountResponse(
            UUID id,
            UUID propertyId,
            UtilityType utilityType,
            String providerName,
            String accountNumber,
            boolean equalPaymentPlan) {
    }

    /** A utility account with its property's name, for pickers that span properties. */
    public record UtilityAccountSummary(
            UUID id,
            UUID propertyId,
            String propertyName,
            UtilityType utilityType,
            String providerName,
            String accountNumber,
            boolean equalPaymentPlan) {
    }
}
