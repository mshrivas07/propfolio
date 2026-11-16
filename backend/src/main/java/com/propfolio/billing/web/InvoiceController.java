package com.propfolio.billing.web;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.propfolio.billing.InvoiceQueryService;
import com.propfolio.billing.InvoiceStatus;
import com.propfolio.billing.web.InvoiceDtos.DueDateRequest;
import com.propfolio.billing.web.InvoiceDtos.InvoiceDetail;
import com.propfolio.billing.web.InvoiceDtos.InvoiceSummary;

/**
 * Invoices. In Phase 5A they are created and filled automatically as bills are verified;
 * sending and payments arrive in Phase 5B.
 */
@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceQueryService invoiceQueryService;

    public InvoiceController(InvoiceQueryService invoiceQueryService) {
        this.invoiceQueryService = invoiceQueryService;
    }

    @GetMapping
    public List<InvoiceSummary> list(@RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) UUID propertyId, @RequestParam(required = false) UUID tenantId) {
        return invoiceQueryService.list(status, propertyId, tenantId);
    }

    @GetMapping("/{invoiceId}")
    public InvoiceDetail detail(@PathVariable UUID invoiceId) {
        return invoiceQueryService.detail(invoiceId);
    }

    @PutMapping("/{invoiceId}/due-date")
    public InvoiceDetail updateDueDate(@PathVariable UUID invoiceId, @Valid @RequestBody DueDateRequest request) {
        return invoiceQueryService.updateDueDate(invoiceId, request);
    }
}
