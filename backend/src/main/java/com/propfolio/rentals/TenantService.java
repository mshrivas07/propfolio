package com.propfolio.rentals;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.propfolio.common.error.ConflictException;
import com.propfolio.common.error.InvalidRequestException;
import com.propfolio.common.error.NotFoundException;
import com.propfolio.rentals.web.TenantDtos.TenantRequest;
import com.propfolio.rentals.web.TenantDtos.TenantResponse;
import com.propfolio.security.CurrentLandlord;

/**
 * Tenants: the people who receive invoices. Roommates sharing a unit are one tenant
 * record with several email addresses.
 */
@Service
@Transactional
public class TenantService {

    private final CurrentLandlord currentLandlord;
    private final TenantRepository tenants;
    private final LeaseRepository leases;
    private final LeaseService leaseService;

    public TenantService(CurrentLandlord currentLandlord, TenantRepository tenants, LeaseRepository leases,
            LeaseService leaseService) {
        this.currentLandlord = currentLandlord;
        this.tenants = tenants;
        this.leases = leases;
        this.leaseService = leaseService;
    }

    @Transactional(readOnly = true)
    public List<TenantResponse> list() {
        UUID landlordId = currentLandlord.id();
        return tenants.findAllByLandlordIdOrderByFullNameAsc(landlordId).stream()
                .map(tenant -> toResponse(tenant, landlordId))
                .toList();
    }

    @Transactional(readOnly = true)
    public TenantResponse get(UUID tenantId) {
        UUID landlordId = currentLandlord.id();
        return toResponse(require(tenantId, landlordId), landlordId);
    }

    public TenantResponse create(TenantRequest request) {
        UUID landlordId = currentLandlord.id();
        Tenant tenant = new Tenant(landlordId, request.fullName().trim(), normalizeEmails(request.emails()));
        apply(tenant, request);
        return toResponse(tenants.save(tenant), landlordId);
    }

    public TenantResponse update(UUID tenantId, TenantRequest request) {
        UUID landlordId = currentLandlord.id();
        Tenant tenant = require(tenantId, landlordId);
        tenant.setFullName(request.fullName().trim());
        tenant.setEmails(new ArrayList<>(normalizeEmails(request.emails())));
        apply(tenant, request);
        return toResponse(tenant, landlordId);
    }

    public void delete(UUID tenantId) {
        UUID landlordId = currentLandlord.id();
        Tenant tenant = require(tenantId, landlordId);
        if (leases.existsByTenantId(tenantId)) {
            throw new ConflictException(
                    "%s has leases. Delete their leases first.".formatted(tenant.getFullName()));
        }
        tenants.delete(tenant);
    }

    /** Trims, lower-cases and removes duplicates, keeping the first-entered order. */
    static List<String> normalizeEmails(List<String> emails) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (emails != null) {
            for (String email : emails) {
                if (email != null && !email.isBlank()) {
                    unique.add(email.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        if (unique.isEmpty()) {
            throw new InvalidRequestException("emails", "Add at least one email address");
        }
        return List.copyOf(unique);
    }

    private Tenant require(UUID tenantId, UUID landlordId) {
        return tenants.findByIdAndLandlordId(tenantId, landlordId).orElseThrow(() -> new NotFoundException("Tenant"));
    }

    private static void apply(Tenant tenant, TenantRequest request) {
        tenant.setPhone(RentalMapper.clean(request.phone()));
        tenant.setNotes(RentalMapper.clean(request.notes()));
    }

    private TenantResponse toResponse(Tenant tenant, UUID landlordId) {
        return new TenantResponse(tenant.getId(), tenant.getFullName(), List.copyOf(tenant.getEmails()),
                tenant.getPhone(), tenant.getNotes(), leaseService.toResponses(
                        leases.findAllByLandlordIdAndTenantIdOrderByStartDateDesc(landlordId, tenant.getId())));
    }
}
