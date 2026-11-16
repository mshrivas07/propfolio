package com.propfolio.billing;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, UUID> {

    List<InvoiceLine> findAllByInvoiceIdOrderByCoveredStartAsc(UUID invoiceId);

    List<InvoiceLine> findAllByInvoiceIdIn(List<UUID> invoiceIds);

    /** Used by documents to block deleting a bill that appears on an invoice. */
    boolean existsByBillId(UUID billId);

    /**
     * Bills already charged to this lease on a locked (non-OPEN) invoice. They are never charged
     * to the lease again, even if the lease dates change later.
     */
    @Query("""
            select distinct l.billId from InvoiceLine l, Invoice i
            where l.invoiceId = i.id
              and i.leaseId = :leaseId
              and i.status <> com.propfolio.billing.InvoiceStatus.OPEN
              and l.voided = false
            """)
    List<UUID> findBillIdsOnLockedInvoices(@Param("leaseId") UUID leaseId);

    /** Used by documents to block un-verifying a bill that is on a locked (non-OPEN) invoice. */
    @Query("""
            select count(l) from InvoiceLine l, Invoice i
            where l.invoiceId = i.id
              and l.billId = :billId
              and i.status <> com.propfolio.billing.InvoiceStatus.OPEN
              and l.voided = false
            """)
    long countOnLockedInvoices(@Param("billId") UUID billId);
}
