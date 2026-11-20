package com.propfolio.billing;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.propfolio.common.error.NotFoundException;
import com.propfolio.rentals.Lease;
import com.propfolio.rentals.LeaseRepository;
import com.propfolio.security.CurrentLandlord;

/**
 * Every invoice status change goes through here, checked by {@link InvoiceRules}. Phase 6 (Gmail) calls
 * {@link #markEmailDrafted} and {@link #markSent}; payments call {@link #applyReceived}.
 * The invoice row is locked for the length of each change.
 */
@Service
@Transactional
public class InvoiceLifecycleService {

    private final CurrentLandlord currentLandlord;
    private final InvoiceRepository invoices;
    private final InvoiceLineRepository lines;
    private final LeaseRepository leases;
    private final DraftInvoiceService draftInvoices;
    private final EntityManager entityManager;
    private final Clock clock;

    public InvoiceLifecycleService(CurrentLandlord currentLandlord, InvoiceRepository invoices,
            InvoiceLineRepository lines, LeaseRepository leases, DraftInvoiceService draftInvoices,
            EntityManager entityManager, Clock clock) {
        this.currentLandlord = currentLandlord;
        this.invoices = invoices;
        this.lines = lines;
        this.leases = leases;
        this.draftInvoices = draftInvoices;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    /** "Create invoice": freeze the OPEN invoice. Later charges start a new OPEN invoice. */
    public Invoice createInvoice(UUID invoiceId) {
        Invoice invoice = lock(invoiceId);
        InvoiceRules.require(InvoiceAction.CREATE_INVOICE, invoice.getStatus(), chargeCount(invoice));
        invoice.setStatus(InvoiceStatus.INVOICE_CREATED);
        invoice.setInvoiceCreatedAt(Instant.now(clock));
        return invoice;
    }

    /**
     * "Delete" (before SENT only) undoes "Create invoice": the invoice becomes OPEN again with the same
     * number, and charges collected meanwhile on the newer OPEN invoice move back into it (the newer one
     * is removed; its number stays unused).
     */
    public Invoice undoCreate(UUID invoiceId) {
        Invoice invoice = lock(invoiceId);
        InvoiceRules.require(InvoiceAction.DELETE, invoice.getStatus(), chargeCount(invoice));

        invoices.findFirstByLeaseIdAndStatus(invoice.getLeaseId(), InvoiceStatus.OPEN).ifPresent(newer -> {
            lines.deleteAll(lines.findAllByInvoiceIdOrderByCoveredStartAsc(newer.getId()));
            entityManager.flush();
            invoices.delete(newer);
            entityManager.flush(); // one OPEN invoice per lease: remove it before reopening this one
        });

        invoice.setStatus(InvoiceStatus.OPEN);
        invoice.setInvoiceCreatedAt(null);
        invoice.setEmailDraftId(null); // Phase 6 also deletes the Gmail draft
        invoice.setEmailDraftedAt(null);
        entityManager.flush();
        draftInvoices.rebuildLease(lease(invoice)); // pulls the newer charges in, recalculates
        return invoice;
    }

    /** Phase 6: a Gmail draft was created (or replaced) for this invoice. */
    public Invoice markEmailDrafted(UUID invoiceId, String emailDraftId) {
        Invoice invoice = lock(invoiceId);
        InvoiceRules.require(InvoiceAction.CREATE_EMAIL_DRAFT, invoice.getStatus(), chargeCount(invoice));
        invoice.setStatus(InvoiceStatus.EMAIL_DRAFTED);
        invoice.setEmailDraftId(emailDraftId);
        invoice.setEmailDraftedAt(Instant.now(clock));
        return invoice;
    }

    /** Phase 6: "Mark as sent". */
    public Invoice markSent(UUID invoiceId) {
        Invoice invoice = lock(invoiceId);
        InvoiceRules.require(InvoiceAction.MARK_SENT, invoice.getStatus(), chargeCount(invoice));
        invoice.setStatus(InvoiceStatus.SENT);
        invoice.setSentAt(Instant.now(clock));
        return invoice;
    }

    /**
     * "Cancel invoice" (sent by mistake, nothing received): CANCELLED and kept for the record. Its charges
     * are voided and recalculated onto the lease's OPEN invoice.
     */
    public Invoice cancel(UUID invoiceId) {
        Invoice invoice = lock(invoiceId);
        InvoiceRules.require(InvoiceAction.CANCEL, invoice.getStatus(), chargeCount(invoice));
        lines.findAllByInvoiceIdOrderByCoveredStartAsc(invoice.getId()).forEach(line -> line.setVoided(true));
        invoice.setStatus(InvoiceStatus.CANCELLED);
        invoice.setCancelledAt(Instant.now(clock));
        entityManager.flush(); // void first, so the same bill days can go on the OPEN invoice
        draftInvoices.rebuildLease(lease(invoice));
        return invoice;
    }

    public Invoice archive(UUID invoiceId) {
        Invoice invoice = lock(invoiceId);
        InvoiceRules.require(InvoiceAction.ARCHIVE, invoice.getStatus(), chargeCount(invoice));
        invoice.setStatus(InvoiceStatus.ARCHIVED);
        invoice.setArchivedAt(Instant.now(clock));
        return invoice;
    }

    public Invoice unarchive(UUID invoiceId) {
        Invoice invoice = lock(invoiceId);
        InvoiceRules.require(InvoiceAction.UNARCHIVE, invoice.getStatus(), chargeCount(invoice));
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setArchivedAt(null);
        return invoice;
    }

    /** Locks an invoice of the signed-in landlord for a change. */
    public Invoice lock(UUID invoiceId) {
        return invoices.lockByIdAndLandlordId(invoiceId, currentLandlord.id())
                .orElseThrow(() -> new NotFoundException("Invoice"));
    }

    /**
     * After a payment is added or deleted: SENT, PARTIALLY_PAID or PAID from the amount received.
     * The caller has locked the invoice and checked the payment rules.
     */
    public void applyReceived(Invoice invoice, BigDecimal received) {
        invoice.setStatus(InvoiceRules.statusForReceived(received, invoice.getTotalAmount()));
    }

    private int chargeCount(Invoice invoice) {
        List<InvoiceLine> invoiceLines = lines.findAllByInvoiceIdOrderByCoveredStartAsc(invoice.getId());
        return (int) invoiceLines.stream().filter(line -> !line.isVoided()).count();
    }

    private Lease lease(Invoice invoice) {
        return leases.findById(invoice.getLeaseId()).orElseThrow(() -> new NotFoundException("Lease"));
    }
}
