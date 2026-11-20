package com.propfolio.billing;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByIdAndLandlordId(UUID id, UUID landlordId);

    /** Locks the invoice row until the transaction ends, so status changes and payments happen one at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id = :id and i.landlordId = :landlordId")
    Optional<Invoice> lockByIdAndLandlordId(@Param("id") UUID id, @Param("landlordId") UUID landlordId);

    List<Invoice> findAllByLeaseIdIn(List<UUID> leaseIds);

    /** The lease's invoice collecting charges (at most one). */
    Optional<Invoice> findFirstByLeaseIdAndStatus(UUID leaseId, InvoiceStatus status);

    List<Invoice> findAllByLandlordIdOrderByCreatedAtDesc(UUID landlordId);

    /** Used by rentals to block deleting a unit that has been invoiced. */
    boolean existsByUnitId(UUID unitId);

    /** Used by rentals to block deleting a lease that has been invoiced. */
    boolean existsByLeaseId(UUID leaseId);

    /** True if the lease has any invoice beyond the one collecting charges. */
    boolean existsByLeaseIdAndStatusNot(UUID leaseId, InvoiceStatus status);
}
