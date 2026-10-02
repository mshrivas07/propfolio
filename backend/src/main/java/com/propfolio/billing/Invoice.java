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
 * An invoice to one tenant (via their lease) for a date range.
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

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "email_draft_id")
    private String emailDraftId;

    @Column(name = "email_drafted_at")
    private Instant emailDraftedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "voided_at")
    private Instant voidedAt;

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

    public Instant getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(Instant paidAt) {
        this.paidAt = paidAt;
    }

    public Instant getVoidedAt() {
        return voidedAt;
    }

    public void setVoidedAt(Instant voidedAt) {
        this.voidedAt = voidedAt;
    }
}
