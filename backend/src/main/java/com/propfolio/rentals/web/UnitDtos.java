package com.propfolio.rentals.web;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request and response bodies for units.
 */
public final class UnitDtos {

    private UnitDtos() {
    }

    public record UnitRequest(
            @NotBlank(message = "Enter a name for the unit") @Size(max = 80) String name,
            @Size(max = 500) String notes) {
    }

    public record UnitResponse(
            UUID id,
            UUID propertyId,
            String name,
            String notes,
            List<LeaseDtos.LeaseResponse> leases) {
    }
}
