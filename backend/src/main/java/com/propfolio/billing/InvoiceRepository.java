package com.propfolio.billing;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByIdAndLandlordId(UUID id, UUID landlordId);

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
