package com.propfolio.billing.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

import com.propfolio.billing.InvoiceStatus;
import com.propfolio.rentals.ShareType;
import com.propfolio.rentals.UtilityType;

/**
 * Request and response bodies for /api/invoices.
 */
public final class InvoiceDtos {

    private InvoiceDtos() {
    }

    /** One invoice in a list. Period fields are null while an OPEN invoice has no charges. */
    public record InvoiceSummary(
            UUID id,
            String invoiceNumber,
            InvoiceStatus status,
            UUID leaseId,
            UUID tenantId,
            String tenantName,
            UUID unitId,
            String unitName,
            UUID propertyId,
            String propertyName,
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal totalAmount,
            LocalDate dueDate,
            int chargeCount,
            Instant createdAt) {
    }

    /** One charge: the tenant's share of one bill. */
    public record Charge(
            UUID id,
            UUID billId,
            String providerName,
            UtilityType utilityType,
            LocalDate billPeriodStart,
            LocalDate billPeriodEnd,
            BigDecimal splitAmount,
            LocalDate coveredStart,
            LocalDate coveredEnd,
            int daysCovered,
            int billPeriodDays,
            ShareType shareType,
            BigDecimal shareValue,
            BigDecimal amount) {
    }

    public record InvoiceDetail(InvoiceSummary invoice, List<Charge> charges) {
    }

    public record DueDateRequest(@NotNull(message = "Choose a due date") LocalDate dueDate) {
    }
}
