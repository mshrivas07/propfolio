package com.propfolio.payments.web;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.propfolio.billing.web.InvoiceDtos.InvoiceDetail;
import com.propfolio.billing.web.InvoiceDtos.PaymentRequest;
import com.propfolio.payments.PaymentService;

/**
 * Payments. Both calls return the updated invoice.
 */
@RestController
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/api/invoices/{invoiceId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceDetail record(@PathVariable UUID invoiceId, @Valid @RequestBody PaymentRequest request) {
        return paymentService.record(invoiceId, request);
    }

    @DeleteMapping("/api/payments/{paymentId}")
    public InvoiceDetail delete(@PathVariable UUID paymentId) {
        return paymentService.delete(paymentId);
    }
}
