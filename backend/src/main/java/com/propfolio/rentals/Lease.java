package com.propfolio.rentals;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.propfolio.common.persistence.LandlordOwnedEntity;

/**
 * Links a tenant to a unit for a date range. Drives proration.
 */
@Entity
@Table(name = "lease")
public class Lease extends LandlordOwnedEntity {

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    protected Lease() {
        // for JPA
    }

    public Lease(UUID landlordId, UUID unitId, UUID tenantId, LocalDate startDate) {
        super(landlordId);
        this.unitId = unitId;
        this.tenantId = tenantId;
        this.startDate = startDate;
    }

    public UUID getUnitId() {
        return unitId;
    }

    public void setUnitId(UUID unitId) {
        this.unitId = unitId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }
}
