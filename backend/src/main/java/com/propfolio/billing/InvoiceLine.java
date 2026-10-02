package com.propfolio.billing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.propfolio.common.persistence.LandlordOwnedEntity;
import com.propfolio.rentals.ShareType;

/**
 * One bill portion on an invoice. Share values are copied so old invoices never change.
 */
@Entity
@Table(name = "invoice_line")
public class InvoiceLine extends LandlordOwnedEntity {

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "bill_id", nullable = false)
    private UUID billId;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "covered_start", nullable = false)
    private LocalDate coveredStart;

    @Column(name = "covered_end", nullable = false)
    private LocalDate coveredEnd;

    @Column(name = "days_covered", nullable = false)
    private int daysCovered;

    @Column(name = "bill_period_days", nullable = false)
    private int billPeriodDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "share_type", nullable = false)
    private ShareType shareType;

    @Column(name = "share_value", nullable = false, precision = 12, scale = 4)
    private BigDecimal shareValue;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "is_void", nullable = false)
    private boolean voided;

    protected InvoiceLine() {
        // for JPA
    }

    public InvoiceLine(UUID landlordId, UUID invoiceId, UUID billId, UUID unitId, LocalDate coveredStart, LocalDate coveredEnd, int daysCovered, int billPeriodDays, ShareType shareType, BigDecimal shareValue, BigDecimal amount) {
        super(landlordId);
        this.invoiceId = invoiceId;
        this.billId = billId;
        this.unitId = unitId;
        this.coveredStart = coveredStart;
        this.coveredEnd = coveredEnd;
        this.daysCovered = daysCovered;
        this.billPeriodDays = billPeriodDays;
        this.shareType = shareType;
        this.shareValue = shareValue;
        this.amount = amount;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(UUID invoiceId) {
        this.invoiceId = invoiceId;
    }

    public UUID getBillId() {
        return billId;
    }

    public void setBillId(UUID billId) {
        this.billId = billId;
    }

    public UUID getUnitId() {
        return unitId;
    }

    public void setUnitId(UUID unitId) {
        this.unitId = unitId;
    }

    public LocalDate getCoveredStart() {
        return coveredStart;
    }

    public void setCoveredStart(LocalDate coveredStart) {
        this.coveredStart = coveredStart;
    }

    public LocalDate getCoveredEnd() {
        return coveredEnd;
    }

    public void setCoveredEnd(LocalDate coveredEnd) {
        this.coveredEnd = coveredEnd;
    }

    public int getDaysCovered() {
        return daysCovered;
    }

    public void setDaysCovered(int daysCovered) {
        this.daysCovered = daysCovered;
    }

    public int getBillPeriodDays() {
        return billPeriodDays;
    }

    public void setBillPeriodDays(int billPeriodDays) {
        this.billPeriodDays = billPeriodDays;
    }

    public ShareType getShareType() {
        return shareType;
    }

    public void setShareType(ShareType shareType) {
        this.shareType = shareType;
    }

    public BigDecimal getShareValue() {
        return shareValue;
    }

    public void setShareValue(BigDecimal shareValue) {
        this.shareValue = shareValue;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public boolean isVoided() {
        return voided;
    }

    public void setVoided(boolean voided) {
        this.voided = voided;
    }
}
