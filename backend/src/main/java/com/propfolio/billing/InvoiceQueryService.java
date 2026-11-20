package com.propfolio.billing;

import java.math.BigDecimal;
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
import com.propfolio.billing.web.InvoiceDtos.PaymentResponse;
import com.propfolio.billing.web.InvoiceDtos.UnpaidInvoice;
import com.propfolio.common.error.NotFoundException;
import com.propfolio.documents.Bill;
import com.propfolio.documents.BillRepository;
import com.propfolio.payments.Payment;
import com.propfolio.payments.PaymentRepository;
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
import com.propfolio.security.Landlord;
import com.propfolio.security.LandlordRepository;

/**
 * Reading invoices (list and detail, including payments and what else the tenant owes) and the due date edit.
 * Charges are only ever written by {@link DraftInvoiceService}; status changes by {@link InvoiceLifecycleService}.
 */
@Service
@Transactional(readOnly = true)
public class InvoiceQueryService {

    private final CurrentLandlord currentLandlord;
    private final InvoiceRepository invoices;
    private final InvoiceLineRepository lines;
    private final PaymentRepository payments;
    private final LeaseRepository leases;
    private final UnitRepository units;
    private final PropertyRepository properties;
    private final TenantRepository tenants;
    private final BillRepository bills;
    private final UtilityAccountRepository utilityAccounts;
    private final LandlordRepository landlords;

    public InvoiceQueryService(CurrentLandlord currentLandlord, InvoiceRepository invoices, InvoiceLineRepository lines,
            PaymentRepository payments, LeaseRepository leases, UnitRepository units, PropertyRepository properties,
            TenantRepository tenants, BillRepository bills, UtilityAccountRepository utilityAccounts,
            LandlordRepository landlords) {
        this.currentLandlord = currentLandlord;
        this.invoices = invoices;
        this.lines = lines;
        this.payments = payments;
        this.leases = leases;
        this.units = units;
        this.properties = properties;
        this.tenants = tenants;
        this.bills = bills;
        this.utilityAccounts = utilityAccounts;
        this.landlords = landlords;
    }

    /**
     * Newest first; every filter is optional. Archived invoices are left out unless asked for
     * ({@code includeArchived}, or {@code status=ARCHIVED}).
     */
    public List<InvoiceSummary> list(InvoiceStatus status, UUID propertyId, UUID tenantId, boolean includeArchived) {
        UUID landlordId = currentLandlord.id();
        List<Invoice> rows = invoices.findAllByLandlordIdOrderByCreatedAtDesc(landlordId).stream()
                .filter(invoice -> status == null || invoice.getStatus() == status)
                .filter(invoice -> includeArchived || status == InvoiceStatus.ARCHIVED
                        || invoice.getStatus() != InvoiceStatus.ARCHIVED)
                .toList();
        return summarize(rows).stream()
                .filter(s -> propertyId == null || propertyId.equals(s.propertyId()))
                .filter(s -> tenantId == null || tenantId.equals(s.tenantId()))
                .toList();
    }

    public InvoiceDetail detail(UUID invoiceId) {
        return detail(require(invoiceId));
    }

    public InvoiceDetail detail(Invoice invoice) {
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

        List<PaymentResponse> paymentRows = payments.findAllByInvoiceIdOrderByPaidOnAscCreatedAtAsc(invoice.getId())
                .stream()
                .map(p -> new PaymentResponse(p.getId(), p.getAmount(), p.getPaidOn(), p.getNote(), p.getCreatedAt()))
                .toList();

        InvoiceSummary summary = summarize(List.of(invoice)).getFirst();
        List<UnpaidInvoice> otherUnpaid = summarize(invoices.findAllByLeaseIdIn(List.of(invoice.getLeaseId())).stream()
                .filter(other -> !other.getId().equals(invoice.getId()))
                .filter(other -> InvoiceRules.countsAsOwed(other.getStatus()))
                .sorted(Comparator.comparing(Invoice::getInvoiceNumber))
                .toList()).stream()
                .filter(s -> s.remaining().signum() > 0)
                .map(InvoiceQueryService::unpaid)
                .toList();
        BigDecimal owingHere = InvoiceRules.countsAsOwed(invoice.getStatus()) ? summary.remaining() : BigDecimal.ZERO;
        BigDecimal totalOwing = otherUnpaid.stream().map(UnpaidInvoice::remaining).reduce(owingHere, BigDecimal::add);

        Landlord landlord = landlords.findById(invoice.getLandlordId()).orElse(null);
        Lease lease = leases.findById(invoice.getLeaseId()).orElse(null);
        Tenant tenant = lease == null ? null : tenants.findById(lease.getTenantId()).orElse(null);
        return new InvoiceDetail(summary, charges, paymentRows, otherUnpaid, totalOwing,
                landlord == null ? null : landlord.getDisplayName(), landlord == null ? null : landlord.getEmail(),
                tenant == null ? List.of() : List.copyOf(tenant.getEmails()));
    }

    /** Due dates can be changed until the email draft is made. */
    @Transactional
    public InvoiceDetail updateDueDate(UUID invoiceId, DueDateRequest request) {
        Invoice invoice = require(invoiceId);
        InvoiceRules.require(InvoiceAction.EDIT_DUE_DATE, invoice.getStatus(), 0);
        invoice.setDueDate(request.dueDate());
        return detail(invoice);
    }

    private Invoice require(UUID invoiceId) {
        return invoices.findByIdAndLandlordId(invoiceId, currentLandlord.id())
                .orElseThrow(() -> new NotFoundException("Invoice"));
    }

    /** Summaries with tenant, unit, property, payments and allowed actions, loaded in a few queries. */
    public List<InvoiceSummary> summarize(List<Invoice> rows) {
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
        List<UUID> invoiceIds = rows.stream().map(Invoice::getId).toList();
        Map<UUID, Long> chargeCounts = lines.findAllByInvoiceIdIn(invoiceIds).stream()
                .filter(line -> !line.isVoided())
                .collect(Collectors.groupingBy(InvoiceLine::getInvoiceId, Collectors.counting()));
        Map<UUID, BigDecimal> receivedById = payments.findAllByInvoiceIdIn(invoiceIds).stream()
                .collect(Collectors.groupingBy(Payment::getInvoiceId,
                        Collectors.reducing(BigDecimal.ZERO, Payment::getAmount, BigDecimal::add)));

        return rows.stream().map(invoice -> {
            Lease lease = leaseById.get(invoice.getLeaseId());
            Unit unit = unitById.get(invoice.getUnitId());
            Property property = unit == null ? null : propertyById.get(unit.getPropertyId());
            Tenant tenant = lease == null ? null : tenantById.get(lease.getTenantId());
            int chargeCount = chargeCounts.getOrDefault(invoice.getId(), 0L).intValue();
            BigDecimal received = receivedById.getOrDefault(invoice.getId(), BigDecimal.ZERO);
            BigDecimal remaining = invoice.getStatus() == InvoiceStatus.CANCELLED ? BigDecimal.ZERO
                    : invoice.getTotalAmount().subtract(received).max(BigDecimal.ZERO);
            return new InvoiceSummary(invoice.getId(), invoice.getInvoiceNumber(), invoice.getStatus(),
                    invoice.getLeaseId(), tenant == null ? null : tenant.getId(),
                    tenant == null ? null : tenant.getFullName(), invoice.getUnitId(),
                    unit == null ? null : unit.getName(), property == null ? null : property.getId(),
                    property == null ? null : property.getName(), invoice.getPeriodStart(), invoice.getPeriodEnd(),
                    invoice.getTotalAmount(), received, remaining, invoice.getDueDate(), chargeCount,
                    InvoiceRules.available(invoice.getStatus(), chargeCount), invoice.getCreatedAt(),
                    invoice.getInvoiceCreatedAt(), invoice.getEmailDraftedAt(), invoice.getSentAt(),
                    invoice.getCancelledAt(), invoice.getArchivedAt());
        }).toList();
    }

    static UnpaidInvoice unpaid(InvoiceSummary s) {
        return new UnpaidInvoice(s.id(), s.invoiceNumber(), s.status(), s.totalAmount(), s.remaining(), s.dueDate());
    }

    private static <T> Map<UUID, T> byId(List<T> rows, Function<T, UUID> id) {
        return rows.stream().collect(Collectors.toMap(id, Function.identity()));
    }
}
