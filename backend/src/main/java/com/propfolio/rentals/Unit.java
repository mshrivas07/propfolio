package com.propfolio.rentals;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.propfolio.common.persistence.LandlordOwnedEntity;

/**
 * A rentable unit inside a property (e.g. "Main floor", "Basement").
 */
@Entity
@Table(name = "unit")
public class Unit extends LandlordOwnedEntity {

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "notes")
    private String notes;

    protected Unit() {
        // for JPA
    }

    public Unit(UUID landlordId, UUID propertyId, String name) {
        super(landlordId);
        this.propertyId = propertyId;
        this.name = name;
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    public void setPropertyId(UUID propertyId) {
        this.propertyId = propertyId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
