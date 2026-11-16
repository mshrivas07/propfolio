package com.propfolio.billing;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.propfolio.billing.web.InvoiceDtos.Charge;
import com.propfolio.billing.web.InvoiceDtos.DueDateRequest;
import com.propfolio.billing.web.InvoiceDtos.InvoiceDetail;
import com.propfolio.billing.web.InvoiceDtos.InvoiceSummary;
import com.propfolio.common.error.ConflictException;
import com.propfolio.common.error.NotFoundException;
import com.propfolio.documents.Bill;
import com.propfolio.documents.BillRepository;
import com.propfolio.rentals.Lease;
import com.propfolio.rentals.LeaseRepository;
import com.propfolio.rentals.Property;
import com.propfolio.rentals.PropertyRepository;
import com.propfolio.rentals.Tenant;
import com.propfolio.rentals.TenantRepository;
import com.propfolio.rentals.Unit;
import com.propfolio.rentals.UnitRepository;
import com.propfolio.rentals.UtilityAccount;
import com.propfolio.rentals.UtilityAccountRepository;
import com.propfolio.security.CurrentLandlord;

/**
 * Reading invoices (list and detail) and the one edit drafts allow in Phase 5A: the due date.
 * Charges themselves are only ever written by {@link DraftInvoiceService}.
 */
@Service
@Transactional(readOnly = true)
public class InvoiceQueryService {

    private final CurrentLandlord currentLandlord;
    private final InvoiceRepository invoices;
    private final InvoiceLineRepository lines;
    private final LeaseRepository leases;
    private final UnitRepository units;
    private final PropertyRepository properties;
    private final TenantRepository tenants;
    private final BillRepository bills;
    private final UtilityAccountRepository utilityAccounts;

    public InvoiceQueryService(CurrentLandlord currentLandlord, InvoiceRepository invoices, InvoiceLineRepository lines,
            LeaseRepository leases, UnitRepository units, PropertyRepository properties, TenantRepository tenants,
            BillRepository bills, UtilityAccountRepository utilityAccounts) {
        this.currentLandlord = currentLandlord;
        this.invoices = invoices;
        this.lines = lines;
        this.leases = leases;
        this.units = units;
        this.properties = properties;
        this.tenants = tenants;
        this.bills = bills;
        this.utilityAccounts = utilityAccounts;
    }

    /** Newest first; every filter is optional. */
    public List<InvoiceSummary> list(InvoiceStatus status, UUID propertyId, UUID tenantId) {
        UUID landlordId = currentLandlord.id();
        List<Invoice> rows = invoices.findAllByLandlordIdOrderByCreatedAtDesc(landlordId).stream()
                .filter(invoice -> status == null || invoice.getStatus() == status)
                .toList();
        List<InvoiceSummary> summaries = summarize(rows);
        return summaries.stream()
                .filter(s -> propertyId == null || propertyId.equals(s.propertyId()))
                .filter(s -> tenantId == null || tenantId.equals(s.tenantId()))
                .toList();
    }

    public InvoiceDetail detail(UUID invoiceId) {
        Invoice invoice = require(invoiceId);
        List<InvoiceLine> invoiceLines = lines.findAllByInvoiceIdOrderByCoveredStartAsc(invoice.getId());
        Map<UUID, Bill> billById = byId(bills.findAllById(invoiceLines.stream().map(InvoiceLine::getBillId).toList()),
                Bill::getId);
        Map<UUID, UtilityAccount> accountById = byId(utilityAccounts.findAllById(billById.values().stream()
                .map(Bill::getUtilityAccountId).filter(Objects::nonNull).distinct().toList()), UtilityAccount::getId);

        List<Charge> charges = invoiceLines.stream()
                .sorted(Comparator.comparing(InvoiceLine::getCoveredStart))
                .map(line -> {
                    Bill bill = billById.get(line.getBillId());
                    UtilityAccount account = bill == null || bill.getUtilityAccountId() == null ? null
                            : accountById.get(bill.getUtilityAccountId());
                    return new Charge(line.getId(), line.getBillId(),
                            account != null ? account.getProviderName() : bill == null ? null : bill.getProviderName(),
                            account == null ? null : account.getUtilityType(),
                            bill == null ? null : bill.getPeriodStart(), bill == null ? null : bill.getPeriodEnd(),
                            bill == null ? null : bill.getSplitAmount(),
                            line.getCoveredStart(), line.getCoveredEnd(), line.getDaysCovered(),
                            line.getBillPeriodDays(), line.getShareType(), line.getShareValue(), line.getAmount());
                })
                .toList();
        return new InvoiceDetail(summarize(List.of(invoice)).getFirst(), charges);
    }

    /** Due dates can be changed while the invoice is still OPEN. */
    @Transactional
    public InvoiceDetail updateDueDate(UUID invoiceId, DueDateRequest request) {
        Invoice invoice = require(invoiceId);
        if (invoice.getStatus() != InvoiceStatus.OPEN) {
            throw new ConflictException("Only an open invoice's due date can be changed.");
        }
        invoice.setDueDate(request.dueDate());
        return detail(invoiceId);
    }

    private Invoice require(UUID invoiceId) {
        return invoices.findByIdAndLandlordId(invoiceId, currentLandlord.id())
                .orElseThrow(() -> new NotFoundException("Invoice"));
    }

    private List<InvoiceSummary> summarize(List<Invoice> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, Lease> leaseById = byId(leases.findAllById(rows.stream().map(Invoice::getLeaseId).distinct().toList()),
                Lease::getId);
        Map<UUID, Unit> unitById = byId(units.findAllById(rows.stream().map(Invoice::getUnitId).distinct().toList()),
                Unit::getId);
        Map<UUID, Property> propertyById = byId(properties.findAllById(unitById.values().stream()
                .map(Unit::getPropertyId).distinct().toList()), Property::getId);
        Map<UUID, Tenant> tenantById = byId(tenants.findAllById(leaseById.values().stream()
                .map(Lease::getTenantId).distinct().toList()), Tenant::getId);
        Map<UUID, Long> chargeCounts = lines.findAllByInvoiceIdIn(rows.stream().map(Invoice::getId).toList()).stream()
                .collect(Collectors.groupingBy(InvoiceLine::getInvoiceId, Collectors.counting()));

        return rows.stream().map(invoice -> {
            Lease lease = leaseById.get(invoice.getLeaseId());
            Unit unit = unitById.get(invoice.getUnitId());
            Property property = unit == null ? null : propertyById.get(unit.getPropertyId());
            Tenant tenant = lease == null ? null : tenantById.get(lease.getTenantId());
            return new InvoiceSummary(invoice.getId(), invoice.getInvoiceNumber(), invoice.getStatus(),
                    invoice.getLeaseId(), tenant == null ? null : tenant.getId(),
                    tenant == null ? null : tenant.getFullName(), invoice.getUnitId(),
                    unit == null ? null : unit.getName(), property == null ? null : property.getId(),
                    property == null ? null : property.getName(), invoice.getPeriodStart(), invoice.getPeriodEnd(),
                    invoice.getTotalAmount(), invoice.getDueDate(),
                    chargeCounts.getOrDefault(invoice.getId(), 0L).intValue(), invoice.getCreatedAt());
        }).toList();
    }

    private static <T> Map<UUID, T> byId(List<T> rows, Function<T, UUID> id) {
        return rows.stream().collect(Collectors.toMap(id, Function.identity()));
    }
}
