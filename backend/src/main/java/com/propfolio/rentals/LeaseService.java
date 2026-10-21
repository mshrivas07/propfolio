package com.propfolio.rentals;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.propfolio.billing.InvoiceRepository;
import com.propfolio.common.error.ConflictException;
import com.propfolio.common.error.InvalidRequestException;
import com.propfolio.common.error.NotFoundException;
import com.propfolio.rentals.web.LeaseDtos.LeaseCreateRequest;
import com.propfolio.rentals.web.LeaseDtos.LeaseResponse;
import com.propfolio.rentals.web.LeaseDtos.LeaseStatus;
import com.propfolio.rentals.web.LeaseDtos.LeaseUpdateRequest;
import com.propfolio.security.CurrentLandlord;

/**
 * Who lives in which unit, and when.
 *
 * <p>Rules: end date on or after start date; leases on the same unit never overlap
 * (one active lease per unit; roommates are one tenant with several emails).
 */
@Service
@Transactional
public class LeaseService {

    private final CurrentLandlord currentLandlord;
    private final LeaseRepository leases;
    private final UnitRepository units;
    private final TenantRepository tenants;
    private final PropertyRepository properties;
    private final InvoiceRepository invoices;
    private final Clock clock;

    /** Stands in for "no end date" in overlap checks (inside Postgres' date range). */
    static final LocalDate OPEN_ENDED = LocalDate.of(9999, 12, 31);

    public LeaseService(CurrentLandlord currentLandlord, LeaseRepository leases, UnitRepository units,
            TenantRepository tenants, PropertyRepository properties, InvoiceRepository invoices, Clock clock) {
        this.currentLandlord = currentLandlord;
        this.leases = leases;
        this.units = units;
        this.tenants = tenants;
        this.properties = properties;
        this.invoices = invoices;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<LeaseResponse> listForUnit(UUID unitId) {
        UUID landlordId = currentLandlord.id();
        requireUnit(unitId, landlordId);
        return toResponses(leases.findAllByLandlordIdAndUnitIdOrderByStartDateDesc(landlordId, unitId));
    }

    public LeaseResponse create(UUID unitId, LeaseCreateRequest request) {
        UUID landlordId = currentLandlord.id();
        Unit unit = requireUnit(unitId, landlordId);
        tenants.findByIdAndLandlordId(request.tenantId(), landlordId)
                .orElseThrow(() -> new InvalidRequestException("tenantId", "Choose a tenant from your tenant list"));

        checkDates(request.startDate(), request.endDate());
        checkNoOverlap(unit, request.startDate(), request.endDate(), UUID.randomUUID());

        Lease lease = new Lease(landlordId, unit.getId(), request.tenantId(), request.startDate());
        lease.setEndDate(request.endDate());
        return toResponses(List.of(leases.save(lease))).getFirst();
    }

    public LeaseResponse update(UUID leaseId, LeaseUpdateRequest request) {
        UUID landlordId = currentLandlord.id();
        Lease lease = requireLease(leaseId, landlordId);
        Unit unit = requireUnit(lease.getUnitId(), landlordId);

        checkDates(request.startDate(), request.endDate());
        checkNoOverlap(unit, request.startDate(), request.endDate(), lease.getId());

        lease.setStartDate(request.startDate());
        lease.setEndDate(request.endDate());
        return toResponses(List.of(lease)).getFirst();
    }

    public void delete(UUID leaseId) {
        UUID landlordId = currentLandlord.id();
        Lease lease = requireLease(leaseId, landlordId);
        if (invoices.existsByLeaseId(lease.getId())) {
            throw new ConflictException(
                    "This lease has invoices, so it can't be deleted. Set an end date to end it instead.");
        }
        leases.delete(lease);
    }

    /** Builds responses with unit, property and tenant names, loading each table once. */
    @Transactional(readOnly = true)
    public List<LeaseResponse> toResponses(Collection<Lease> source) {
        if (source.isEmpty()) {
            return List.of();
        }
        Map<UUID, Unit> unitById = byId(units.findAllById(source.stream().map(Lease::getUnitId).toList()),
                Unit::getId);
        Map<UUID, Property> propertyById = byId(properties.findAllById(
                unitById.values().stream().map(Unit::getPropertyId).toList()), Property::getId);
        Map<UUID, Tenant> tenantById = byId(tenants.findAllById(source.stream().map(Lease::getTenantId).toList()),
                Tenant::getId);
        LocalDate today = LocalDate.now(clock);

        return source.stream().map(lease -> {
            Unit unit = unitById.get(lease.getUnitId());
            Property property = unit == null ? null : propertyById.get(unit.getPropertyId());
            Tenant tenant = tenantById.get(lease.getTenantId());
            return new LeaseResponse(lease.getId(), lease.getUnitId(), unit == null ? null : unit.getName(),
                    property == null ? null : property.getId(), property == null ? null : property.getName(),
                    lease.getTenantId(), tenant == null ? null : tenant.getFullName(),
                    lease.getStartDate(), lease.getEndDate(), status(lease, today));
        }).toList();
    }

    static LeaseStatus status(Lease lease, LocalDate today) {
        if (lease.getStartDate().isAfter(today)) {
            return LeaseStatus.UPCOMING;
        }
        if (lease.getEndDate() != null && lease.getEndDate().isBefore(today)) {
            return LeaseStatus.ENDED;
        }
        return LeaseStatus.CURRENT;
    }

    private static void checkDates(LocalDate start, LocalDate end) {
        if (end != null && end.isBefore(start)) {
            throw new InvalidRequestException("endDate", "The end date can't be before the start date");
        }
    }

    private void checkNoOverlap(Unit unit, LocalDate start, LocalDate end, UUID excludeLeaseId) {
        List<Lease> clashes = leases.findOverlapping(unit.getId(), start,
                end == null ? OPEN_ENDED : end, excludeLeaseId);
        if (!clashes.isEmpty()) {
            Lease clash = clashes.getFirst();
            String tenantName = tenants.findById(clash.getTenantId()).map(Tenant::getFullName).orElse("another tenant");
            String until = clash.getEndDate() == null ? "with no end date" : "to " + clash.getEndDate();
            throw new ConflictException("%s already has a lease for %s from %s %s. Leases on the same unit can't overlap."
                    .formatted(unit.getName(), tenantName, clash.getStartDate(), until));
        }
    }

    private Unit requireUnit(UUID unitId, UUID landlordId) {
        return units.findByIdAndLandlordId(unitId, landlordId).orElseThrow(() -> new NotFoundException("Unit"));
    }

    private Lease requireLease(UUID leaseId, UUID landlordId) {
        return leases.findByIdAndLandlordId(leaseId, landlordId).orElseThrow(() -> new NotFoundException("Lease"));
    }

    private static <T> Map<UUID, T> byId(List<T> rows, Function<T, UUID> id) {
        return rows.stream().collect(Collectors.toMap(id, Function.identity()));
    }
}
