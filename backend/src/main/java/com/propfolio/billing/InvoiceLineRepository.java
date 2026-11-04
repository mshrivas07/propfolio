package com.propfolio.billing;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, UUID> {

    /** Used by documents to block deleting a bill that appears on an invoice. */
    boolean existsByBillId(UUID billId);

    /** Used by documents to block un-verifying a bill that is on a live (non-void) invoice. */
    boolean existsByBillIdAndVoidedFalse(UUID billId);
}
