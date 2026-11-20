package com.propfolio.billing.web;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import jakarta.validation.Valid;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.propfolio.billing.BalanceService;
import com.propfolio.billing.Invoice;
import com.propfolio.billing.InvoiceLifecycleService;
import com.propfolio.billing.InvoicePdfService;
import com.propfolio.billing.InvoiceQueryService;
import com.propfolio.billing.InvoiceStatus;
import com.propfolio.billing.web.InvoiceDtos.DueDateRequest;
import com.propfolio.billing.web.InvoiceDtos.InvoiceDetail;
import com.propfolio.billing.web.InvoiceDtos.InvoiceSummary;
import com.propfolio.billing.web.InvoiceDtos.TenantBalance;

/**
 * Invoices: reading, lifecycle moves, PDF, balances. Payments are in PaymentController.
 * Every move returns the updated invoice; refused moves are 409 with a plain explanation.
 */
@RestController
@RequestMapping("/api")
public class InvoiceController {

    private final InvoiceQueryService invoiceQueries;
    private final InvoiceLifecycleService lifecycle;
    private final InvoicePdfService pdfs;
    private final BalanceService balances;

    public InvoiceController(InvoiceQueryService invoiceQueries, InvoiceLifecycleService lifecycle,
            InvoicePdfService pdfs, BalanceService balances) {
        this.invoiceQueries = invoiceQueries;
        this.lifecycle = lifecycle;
        this.pdfs = pdfs;
        this.balances = balances;
    }

    @GetMapping("/invoices")
    public List<InvoiceSummary> list(@RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) UUID propertyId, @RequestParam(required = false) UUID tenantId,
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return invoiceQueries.list(status, propertyId, tenantId, includeArchived);
    }

    @GetMapping("/invoices/{invoiceId}")
    public InvoiceDetail detail(@PathVariable UUID invoiceId) {
        return invoiceQueries.detail(invoiceId);
    }

    @PutMapping("/invoices/{invoiceId}/due-date")
    public InvoiceDetail updateDueDate(@PathVariable UUID invoiceId, @Valid @RequestBody DueDateRequest request) {
        return invoiceQueries.updateDueDate(invoiceId, request);
    }

    /** "Create invoice": OPEN → INVOICE_CREATED. */
    @PostMapping("/invoices/{invoiceId}/create")
    public InvoiceDetail create(@PathVariable UUID invoiceId) {
        return move(invoiceId, lifecycle::createInvoice);
    }

    /** "Delete" before SENT: undo "Create invoice" (back to OPEN, same number). */
    @DeleteMapping("/invoices/{invoiceId}")
    public InvoiceDetail delete(@PathVariable UUID invoiceId) {
        return move(invoiceId, lifecycle::undoCreate);
    }

    @PostMapping("/invoices/{invoiceId}/cancel")
    public InvoiceDetail cancel(@PathVariable UUID invoiceId) {
        return move(invoiceId, lifecycle::cancel);
    }

    @PostMapping("/invoices/{invoiceId}/archive")
    public InvoiceDetail archive(@PathVariable UUID invoiceId) {
        return move(invoiceId, lifecycle::archive);
    }

    @PostMapping("/invoices/{invoiceId}/unarchive")
    public InvoiceDetail unarchive(@PathVariable UUID invoiceId) {
        return move(invoiceId, lifecycle::unarchive);
    }

    /** The invoice as a PDF, shown inline (the browser can save it). */
    @GetMapping("/invoices/{invoiceId}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID invoiceId) {
        InvoicePdfService.Pdf pdf = pdfs.render(invoiceId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(pdf.filename()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(pdf.bytes());
    }

    /** What each tenant owes, for the tenants page. */
    @GetMapping("/balances/tenants")
    public List<TenantBalance> tenantBalances() {
        return balances.forTenants();
    }

    private InvoiceDetail move(UUID invoiceId, Function<UUID, Invoice> action) {
        return invoiceQueries.detail(action.apply(invoiceId));
    }
}
