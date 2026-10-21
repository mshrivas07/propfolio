package com.propfolio.rentals;

import static com.propfolio.rentals.RentalTestData.lease;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.propfolio.common.error.ConflictException;
import com.propfolio.common.error.InvalidRequestException;
import com.propfolio.common.error.NotFoundException;
import com.propfolio.rentals.web.LeaseDtos.LeaseCreateRequest;
import com.propfolio.rentals.web.LeaseDtos.LeaseStatus;
import com.propfolio.rentals.web.LeaseDtos.LeaseUpdateRequest;

class LeaseServiceTest extends RentalServiceTestBase {

    @Test
    void endDateBeforeStartDateIsAFieldError() {
        assertThatThrownBy(() -> leaseService.create(data.upper.id(), lease(data.tenant, "2026-09-01", "2026-08-31")))
                .isInstanceOfSatisfying(InvalidRequestException.class,
                        e -> assertThat(e.getField()).isEqualTo("endDate"));
    }

    @Test
    void sameDayLeaseIsAllowed() {
        var created = leaseService.create(data.upper.id(), lease(data.tenant, "2026-09-01", "2026-09-01"));
        assertThat(created.endDate()).isEqualTo(created.startDate());
    }

    @Test
    void overlappingLeasesOnTheSameUnitAreRejected() {
        leaseService.create(data.upper.id(), lease(data.tenant, "2026-01-01", "2026-06-30"));

        assertThatThrownBy(() -> leaseService.create(data.upper.id(), lease(data.tenant, "2026-06-30", null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Upper")
                .hasMessageContaining("Sam Tenant");
    }

    @Test
    void backToBackLeasesAreAllowed() {
        leaseService.create(data.upper.id(), lease(data.tenant, "2026-01-01", "2026-06-30"));
        leaseService.create(data.upper.id(), lease(data.tenant, "2026-07-01", null));

        assertThat(leaseService.listForUnit(data.upper.id())).hasSize(2);
    }

    @Test
    void anOngoingLeaseBlocksAnyLaterLease() {
        leaseService.create(data.upper.id(), lease(data.tenant, "2026-01-01", null));

        assertThatThrownBy(() -> leaseService.create(data.upper.id(), lease(data.tenant, "2030-01-01", "2030-12-31")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void leasesOnDifferentUnitsMayOverlap() {
        leaseService.create(data.upper.id(), lease(data.tenant, "2026-01-01", null));
        leaseService.create(data.lower.id(), lease(data.tenant, "2026-01-01", null));
    }

    @Test
    void endingALeaseThenStartingTheNextOneWorks() {
        var current = leaseService.create(data.upper.id(), lease(data.tenant, "2026-01-01", null));

        leaseService.update(current.id(), new LeaseUpdateRequest(LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-09-15")));
        var next = leaseService.create(data.upper.id(), lease(data.tenant, "2026-09-16", null));

        assertThat(next.startDate()).isEqualTo(LocalDate.parse("2026-09-16"));
    }

    @Test
    void extendingALeaseIntoTheNextOneIsRejected() {
        var first = leaseService.create(data.upper.id(), lease(data.tenant, "2026-01-01", "2026-06-30"));
        leaseService.create(data.upper.id(), lease(data.tenant, "2026-07-01", null));

        assertThatThrownBy(() -> leaseService.update(first.id(),
                new LeaseUpdateRequest(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-07-15"))))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void editingALeaseDoesNotClashWithItself() {
        var lease = leaseService.create(data.upper.id(), lease(data.tenant, "2026-01-01", null));
        var updated = leaseService.update(lease.id(),
                new LeaseUpdateRequest(LocalDate.parse("2025-12-01"), null));
        assertThat(updated.startDate()).isEqualTo(LocalDate.parse("2025-12-01"));
    }

    @Test
    void unknownTenantIsAFieldError() {
        assertThatThrownBy(() -> leaseService.create(data.upper.id(),
                new LeaseCreateRequest(UUID.randomUUID(), LocalDate.parse("2026-01-01"), null)))
                .isInstanceOfSatisfying(InvalidRequestException.class,
                        e -> assertThat(e.getField()).isEqualTo("tenantId"));
    }

    @Test
    void unknownUnitIsNotFound() {
        assertThatThrownBy(() -> leaseService.create(UUID.randomUUID(), lease(data.tenant, "2026-01-01", null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void statusReflectsToday() {
        LocalDate today = LocalDate.parse("2026-10-19");
        Lease ended = new Lease(landlordId, data.upper.id(), data.tenant.id(), LocalDate.parse("2026-01-01"));
        ended.setEndDate(LocalDate.parse("2026-10-18"));
        Lease current = new Lease(landlordId, data.upper.id(), data.tenant.id(), LocalDate.parse("2026-10-19"));
        Lease upcoming = new Lease(landlordId, data.upper.id(), data.tenant.id(), LocalDate.parse("2026-10-20"));

        assertThat(LeaseService.status(ended, today)).isEqualTo(LeaseStatus.ENDED);
        assertThat(LeaseService.status(current, today)).isEqualTo(LeaseStatus.CURRENT);
        assertThat(LeaseService.status(upcoming, today)).isEqualTo(LeaseStatus.UPCOMING);
    }
}
