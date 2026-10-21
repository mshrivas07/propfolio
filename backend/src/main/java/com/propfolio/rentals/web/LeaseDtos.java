package com.propfolio.rentals.web;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Request and response bodies for leases.
 */
public final class LeaseDtos {

    private LeaseDtos() {
    }

    public record LeaseCreateRequest(
            @NotNull(message = "Choose a tenant") UUID tenantId,
            @NotNull(message = "Enter the start date") LocalDate startDate,
            LocalDate endDate) {
    }

    /** Changes the dates of a lease; setting endDate is how a lease is ended. */
    public record LeaseUpdateRequest(
            @NotNull(message = "Enter the start date") LocalDate startDate,
            LocalDate endDate) {
    }

    public enum LeaseStatus { UPCOMING, CURRENT, ENDED }

    public record LeaseResponse(
            UUID id,
            UUID unitId,
            String unitName,
            UUID propertyId,
            String propertyName,
            UUID tenantId,
            String tenantName,
            LocalDate startDate,
            LocalDate endDate,
            LeaseStatus status) {
    }
}
