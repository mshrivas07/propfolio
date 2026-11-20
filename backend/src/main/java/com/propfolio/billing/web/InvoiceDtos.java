package com.propfolio.billing.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.propfolio.billing.InvoiceAction;
import com.propfolio.billing.InvoiceStatus;
import com.propfolio.rentals.ShareType;
import com.propfolio.rentals.UtilityType;

/**
 * Request and response bodies for /api/invoices, payments and balances.
 */
public final class InvoiceDtos {

    private InvoiceDtos() {
    }

    /**
     * One invoice in a list. Period fields are null while an OPEN invoice has no charges.
     * {@code received} and {@code remaining} come from its payments; {@code actions} are the moves
     * allowed now (see InvoiceRules).
     */
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
            BigDecimal received,
            BigDecimal remaining,
            LocalDate dueDate,
            int chargeCount,
            List<InvoiceAction> actions,
            Instant createdAt,
            Instant invoiceCreatedAt,
            Instant emailDraftedAt,
            Instant sentAt,
            Instant cancelledAt,
            Instant archivedAt) {
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

    public record PaymentResponse(UUID id, BigDecimal amount, LocalDate paidOn, String note, Instant createdAt) {
    }

    /** Another unpaid invoice of the same lease, shown for information only. */
    public record UnpaidInvoice(
            UUID id,
            String invoiceNumber,
            InvoiceStatus status,
            BigDecimal totalAmount,
            BigDecimal remaining,
            LocalDate dueDate) {
    }

    /**
     * @param otherUnpaid other invoices of the same lease with money still owing (not part of this total)
     * @param totalOwing  this invoice's remaining amount plus the other unpaid ones
     */
    public record InvoiceDetail(
            InvoiceSummary invoice,
            List<Charge> charges,
            List<PaymentResponse> payments,
            List<UnpaidInvoice> otherUnpaid,
            BigDecimal totalOwing,
            String landlordName,
            String landlordEmail,
            List<String> tenantEmails) {
    }

    public record DueDateRequest(@NotNull(message = "Choose a due date") LocalDate dueDate) {
    }

    public record PaymentRequest(
            @NotNull(message = "Enter the amount received")
            @DecimalMin(value = "0.01", message = "The amount must be more than $0")
            @Digits(integer = 10, fraction = 2, message = "Use at most 2 decimal places")
            BigDecimal amount,
            @NotNull(message = "Enter the date it was received") LocalDate paidOn,
            @Size(max = 500, message = "Keep the note under 500 characters") String note) {
    }

    /**
     * What a tenant owes.
     *
     * @param outstanding sum of (total − received) over their created, non-cancelled invoices
     * @param nextInvoice what's collecting on their OPEN invoice(s), not owed yet
     */
    public record TenantBalance(
            UUID tenantId,
            String tenantName,
            BigDecimal outstanding,
            BigDecimal nextInvoice,
            List<UnpaidInvoice> unpaidInvoices) {
    }
}
