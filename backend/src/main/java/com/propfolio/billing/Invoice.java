package com.propfolio.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.propfolio.common.persistence.LandlordOwnedEntity;

/**
 * An invoice to one tenant (via their lease). Status rules: {@link InvoiceRules}.
 */
@Entity
@Table(name = "invoice")
public class Invoice extends LandlordOwnedEntity {

    @Column(name = "invoice_number", nullable = false)
    private String invoiceNumber;

    @Column(name = "lease_id", nullable = false)
    private UUID leaseId;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    /** Earliest covered date of its charges; null while an OPEN invoice has no charges. */
    @Column(name = "period_start")
    private LocalDate periodStart;

    /** Latest covered date of its charges; null while an OPEN invoice has no charges. */
    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private InvoiceStatus status = InvoiceStatus.OPEN;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "email_draft_id")
    private String emailDraftId;

    @Column(name = "email_drafted_at")
    private Instant emailDraftedAt;

    @Column(name = "invoice_created_at")
    private Instant invoiceCreatedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected Invoice() {
        // for JPA
    }

    public Invoice(UUID landlordId, String invoiceNumber, UUID leaseId, UUID unitId, LocalDate periodStart, LocalDate periodEnd) {
        super(landlordId);
        this.invoiceNumber = invoiceNumber;
        this.leaseId = leaseId;
        this.unitId = unitId;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public UUID getLeaseId() {
        return leaseId;
    }

    public void setLeaseId(UUID leaseId) {
        this.leaseId = leaseId;
    }

    public UUID getUnitId() {
        return unitId;
    }

    public void setUnitId(UUID unitId) {
        this.unitId = unitId;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDate periodStart) {
        this.periodStart = periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(LocalDate periodEnd) {
        this.periodEnd = periodEnd;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public void setStatus(InvoiceStatus status) {
        this.status = status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public String getEmailDraftId() {
        return emailDraftId;
    }

    public void setEmailDraftId(String emailDraftId) {
        this.emailDraftId = emailDraftId;
    }

    public Instant getEmailDraftedAt() {
        return emailDraftedAt;
    }

    public void setEmailDraftedAt(Instant emailDraftedAt) {
        this.emailDraftedAt = emailDraftedAt;
    }

    public Instant getInvoiceCreatedAt() {
        return invoiceCreatedAt;
    }

    public void setInvoiceCreatedAt(Instant invoiceCreatedAt) {
        this.invoiceCreatedAt = invoiceCreatedAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(Instant archivedAt) {
        this.archivedAt = archivedAt;
    }
}
