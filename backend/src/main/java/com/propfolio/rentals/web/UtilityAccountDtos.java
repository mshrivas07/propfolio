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

    public record UtilityAccountRequest(
            @NotNull(message = "Choose the type of utility") UtilityType utilityType,
            @NotBlank(message = "Enter the provider name") @Size(max = 120) String providerName,
            @Size(max = 60) String accountNumber) {
    }

    public record UtilityAccountResponse(
            UUID id,
            UUID propertyId,
            UtilityType utilityType,
            String providerName,
            String accountNumber) {
    }

    /** A utility account with its property's name, for pickers that span properties. */
    public record UtilityAccountSummary(
            UUID id,
            UUID propertyId,
            String propertyName,
            UtilityType utilityType,
            String providerName,
            String accountNumber) {
    }
}
