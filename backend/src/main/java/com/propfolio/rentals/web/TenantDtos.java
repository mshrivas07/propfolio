package com.propfolio.rentals.web;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * Request and response bodies for /api/tenants.
 */
public final class TenantDtos {

    private TenantDtos() {
    }

    public record TenantRequest(
            @NotBlank(message = "Enter the tenant's name") @Size(max = 120) String fullName,
            @NotEmpty(message = "Add at least one email address")
            @Size(max = 10, message = "Add at most 10 email addresses")
            List<@NotBlank(message = "Email can't be blank")
                 @Email(message = "Enter a valid email address")
                 @Size(max = 254) String> emails,
            @Size(max = 40) String phone,
            @Size(max = 500) String notes) {
    }

    public record TenantResponse(
            UUID id,
            String fullName,
            List<String> emails,
            String phone,
            String notes,
            List<LeaseDtos.LeaseResponse> leases) {
    }
}
