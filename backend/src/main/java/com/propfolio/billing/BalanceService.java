package com.propfolio.billing;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.propfolio.billing.web.InvoiceDtos.InvoiceSummary;
import com.propfolio.billing.web.InvoiceDtos.TenantBalance;
import com.propfolio.rentals.Tenant;
import com.propfolio.rentals.TenantRepository;
import com.propfolio.security.CurrentLandlord;

/**
 * What each tenant owes.
 *
 * <pre>
 * outstanding  = Σ (total − received) over invoices that are created and not cancelled
 *                (INVOICE_CREATED, EMAIL_DRAFTED, SENT, PARTIALLY_PAID, PAID, ARCHIVED)
 * next invoice = total of the OPEN invoice(s): collecting, not owed yet
 * </pre>
 */
@Service
@Transactional(readOnly = true)
public class BalanceService {

    private final CurrentLandlord currentLandlord;
    private final InvoiceRepository invoices;
    private final TenantRepository tenants;
    private final InvoiceQueryService invoiceQueries;

    public BalanceService(CurrentLandlord currentLandlord, InvoiceRepository invoices, TenantRepository tenants,
            InvoiceQueryService invoiceQueries) {
        this.currentLandlord = currentLandlord;
        this.invoices = invoices;
        this.tenants = tenants;
        this.invoiceQueries = invoiceQueries;
    }

    /** Every tenant of the landlord, including those who owe nothing, by name. */
    public List<TenantBalance> forTenants() {
        UUID landlordId = currentLandlord.id();
        Map<UUID, List<InvoiceSummary>> byTenant = invoiceQueries
                .summarize(invoices.findAllByLandlordIdOrderByCreatedAtDesc(landlordId)).stream()
                .filter(s -> s.tenantId() != null)
                .collect(Collectors.groupingBy(InvoiceSummary::tenantId));

        return tenants.findAllByLandlordIdOrderByFullNameAsc(landlordId).stream()
                .map(tenant -> balance(tenant, byTenant.getOrDefault(tenant.getId(), List.of())))
                .toList();
    }

    static TenantBalance balance(Tenant tenant, List<InvoiceSummary> invoices) {
        List<InvoiceSummary> owed = invoices.stream().filter(s -> InvoiceRules.countsAsOwed(s.status())).toList();
        BigDecimal outstanding = owed.stream().map(InvoiceSummary::remaining).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal next = invoices.stream()
                .filter(s -> s.status() == InvoiceStatus.OPEN)
                .map(InvoiceSummary::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new TenantBalance(tenant.getId(), tenant.getFullName(), outstanding, next,
                owed.stream()
                        .filter(s -> s.remaining().signum() > 0)
                        .sorted(Comparator.comparing(InvoiceSummary::invoiceNumber))
                        .map(InvoiceQueryService::unpaid)
                        .toList());
    }
}
