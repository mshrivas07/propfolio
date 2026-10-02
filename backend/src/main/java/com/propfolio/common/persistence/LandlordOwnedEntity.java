package com.propfolio.common.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

/**
 * Base for every table that belongs to one landlord. Services must always filter by
 * {@code landlordId}; this is how data is kept separate between landlords.
 *
 * <p>Other tables are referenced by id (plain UUID columns), not JPA relationships,
 * to keep loading explicit and avoid lazy-loading surprises.
 */
@MappedSuperclass
public abstract class LandlordOwnedEntity extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "landlord_id", nullable = false, updatable = false)
    private UUID landlordId;

    protected LandlordOwnedEntity() {
    }

    protected LandlordOwnedEntity(UUID landlordId) {
        this.landlordId = landlordId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getLandlordId() {
        return landlordId;
    }
}
