package com.propfolio.payments;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.propfolio.billing.Invoice;
import com.propfolio.billing.InvoiceAction;
import com.propfolio.billing.InvoiceLifecycleService;
import com.propfolio.billing.InvoiceQueryService;
import com.propfolio.billing.InvoiceRules;
import com.propfolio.billing.web.InvoiceDtos.InvoiceDetail;
import com.propfolio.billing.web.InvoiceDtos.PaymentRequest;
import com.propfolio.common.error.InvalidRequestException;
import com.propfolio.common.error.NotFoundException;
import com.propfolio.security.CurrentLandlord;

/**
 * Recording and deleting payments. Each payment belongs to one invoice, which must be SENT or
 * PARTIALLY_PAID; a payment can't be more than what's still owed (credit is MVP2). The invoice's status
 * follows the amount received.
 */
@Service
@Transactional
public class PaymentService {

    private final CurrentLandlord currentLandlord;
    private final PaymentRepository payments;
    private final InvoiceLifecycleService lifecycle;
    private final InvoiceQueryService invoiceQueries;
    private final Clock clock;

    public PaymentService(CurrentLandlord currentLandlord, PaymentRepository payments,
            InvoiceLifecycleService lifecycle, InvoiceQueryService invoiceQueries, Clock clock) {
        this.currentLandlord = currentLandlord;
        this.payments = payments;
        this.lifecycle = lifecycle;
        this.invoiceQueries = invoiceQueries;
        this.clock = clock;
    }

    public InvoiceDetail record(UUID invoiceId, PaymentRequest request) {
        Invoice invoice = lifecycle.lock(invoiceId);
        InvoiceRules.require(InvoiceAction.RECORD_PAYMENT, invoice.getStatus(), 0);
        if (request.paidOn().isAfter(LocalDate.now(clock))) {
            throw new InvalidRequestException("paidOn", "The date received can't be in the future");
        }
        BigDecimal received = payments.sumByInvoiceId(invoice.getId());
        BigDecimal remaining = invoice.getTotalAmount().subtract(received);
        if (request.amount().compareTo(remaining) > 0) {
            throw new InvalidRequestException("amount",
                    "Only $%s is still owed on this invoice".formatted(remaining.toPlainString()));
        }
        Payment payment = new Payment(invoice.getLandlordId(), invoice.getId(), request.amount(), request.paidOn());
        String note = request.note() == null ? null : request.note().trim();
        payment.setNote(note == null || note.isEmpty() ? null : note);
        payments.save(payment);
        lifecycle.applyReceived(invoice, received.add(request.amount()));
        return invoiceQueries.detail(invoice);
    }

    /** Deleting a payment recalculates the status (back to SENT when nothing is left). */
    public InvoiceDetail delete(UUID paymentId) {
        Payment payment = payments.findByIdAndLandlordId(paymentId, currentLandlord.id())
                .orElseThrow(() -> new NotFoundException("Payment"));
        Invoice invoice = lifecycle.lock(payment.getInvoiceId());
        InvoiceRules.require(InvoiceAction.DELETE_PAYMENT, invoice.getStatus(), 0);
        payments.delete(payment);
        payments.flush();
        lifecycle.applyReceived(invoice, payments.sumByInvoiceId(invoice.getId()));
        return invoiceQueries.detail(invoice);
    }
}
