package com.propfolio.billing;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    /** Used by rentals to block deleting a unit that has been invoiced. */
    boolean existsByUnitId(UUID unitId);

    /** Used by rentals to block deleting a lease that has been invoiced. */
    boolean existsByLeaseId(UUID leaseId);
}
