package com.propfolio.billing;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.propfolio.documents.Bill;
import com.propfolio.documents.BillRepository;
import com.propfolio.rentals.Lease;
import com.propfolio.rentals.LeaseRepository;
import com.propfolio.rentals.SplitRule;
import com.propfolio.rentals.SplitRuleRepository;

/**
 * Keeps each lease's OPEN invoice in step with its verified bills.
 *
 * <p>The OPEN invoice is <b>rebuilt</b>, never patched: expected charges = the tenant's share of every
 * verified bill on the unit's split-rule accounts overlapping the lease, minus bills already charged
 * to the lease on a locked (non-OPEN) invoice. Running it twice changes nothing.
 *
 * <p>Called whenever something that affects charges changes: a bill is verified or un-verified,
 * a split rule is saved or deleted, a lease is created, edited or deleted.
 */
@Service
@Transactional
public class DraftInvoiceService {

    static final int DUE_IN_DAYS = 15;
    /** Stands in for "no end date" when looking for overlapping bills. */
    private static final LocalDate OPEN_ENDED = LocalDate.of(9999, 12, 31);

    private record ExpectedCharge(UUID billId, SplitRule rule, ChargeCalculator.Charge charge) {
    }

    private final LeaseRepository leases;
    private final SplitRuleRepository splitRules;
    private final BillRepository bills;
    private final InvoiceRepository invoices;
    private final InvoiceLineRepository lines;
    private final InvoiceNumberService numbers;
    private final EntityManager entityManager;
    private final Clock clock;

    public DraftInvoiceService(LeaseRepository leases, SplitRuleRepository splitRules, BillRepository bills,
            InvoiceRepository invoices, InvoiceLineRepository lines, InvoiceNumberService numbers,
            EntityManager entityManager, Clock clock) {
        this.leases = leases;
        this.splitRules = splitRules;
        this.bills = bills;
        this.invoices = invoices;
        this.lines = lines;
        this.numbers = numbers;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    /** A bill was verified or un-verified: rebuild every lease whose unit shares that bill. */
    public void rebuildForBill(Bill bill) {
        if (bill.getUtilityAccountId() != null) {
            rebuildForUtilityAccount(bill.getUtilityAccountId());
        }
    }

    public void rebuildForUtilityAccount(UUID utilityAccountId) {
        splitRules.findAllByUtilityAccountId(utilityAccountId).stream()
                .map(SplitRule::getUnitId)
                .distinct()
                .forEach(this::rebuildForUnit);
    }

    /** A split rule changed: rebuild every lease on the unit. */
    public void rebuildForUnit(UUID unitId) {
        leases.findAllByUnitId(unitId).forEach(this::rebuildLease);
    }

    /** Brings the lease's OPEN invoice in line with what it should be charged now. */
    public void rebuildLease(Lease lease) {
        List<ExpectedCharge> expected = expectedCharges(lease);

        Invoice open = invoices.findFirstByLeaseIdAndStatus(lease.getId(), InvoiceStatus.OPEN).orElse(null);
        if (open == null) {
            if (expected.isEmpty()) {
                return;
            }
            open = createOpenInvoice(lease);
        }

        Map<UUID, InvoiceLine> existing = lines.findAllByInvoiceIdOrderByCoveredStartAsc(open.getId()).stream()
                .collect(Collectors.toMap(InvoiceLine::getBillId, Function.identity(), (a, b) -> a));
        Set<UUID> expectedBillIds = expected.stream().map(ExpectedCharge::billId).collect(Collectors.toSet());

        // Remove first, and write the removal before adding anything, so the database's
        // no-double-billing rule never sees the old and new versions of a charge together.
        List<InvoiceLine> stale = existing.values().stream()
                .filter(line -> !expectedBillIds.contains(line.getBillId()))
                .toList();
        if (!stale.isEmpty()) {
            lines.deleteAll(stale);
            entityManager.flush();
        }

        for (ExpectedCharge e : expected) {
            ChargeCalculator.Charge c = e.charge();
            InvoiceLine line = existing.get(e.billId());
            if (line == null) {
                lines.save(new InvoiceLine(lease.getLandlordId(), open.getId(), e.billId(), lease.getUnitId(),
                        c.coveredStart(), c.coveredEnd(), c.daysCovered(), c.billPeriodDays(),
                        e.rule().getShareType(), e.rule().getShareValue(), c.amount()));
            } else {
                line.setCoveredStart(c.coveredStart());
                line.setCoveredEnd(c.coveredEnd());
                line.setDaysCovered(c.daysCovered());
                line.setBillPeriodDays(c.billPeriodDays());
                line.setShareType(e.rule().getShareType());
                line.setShareValue(e.rule().getShareValue());
                line.setAmount(c.amount());
            }
        }

        open.setTotalAmount(expected.stream().map(e -> e.charge().amount()).reduce(BigDecimal.ZERO, BigDecimal::add));
        open.setPeriodStart(expected.stream().map(e -> e.charge().coveredStart()).min(Comparator.naturalOrder())
                .orElse(null));
        open.setPeriodEnd(expected.stream().map(e -> e.charge().coveredEnd()).max(Comparator.naturalOrder())
                .orElse(null));
    }

    /** Before a lease is deleted: drop its OPEN invoice and charges (locked invoices block deletion). */
    public void removeOpenInvoice(Lease lease) {
        invoices.findFirstByLeaseIdAndStatus(lease.getId(), InvoiceStatus.OPEN).ifPresent(open -> {
            lines.deleteAll(lines.findAllByInvoiceIdOrderByCoveredStartAsc(open.getId()));
            entityManager.flush();
            invoices.delete(open);
        });
    }

    private List<ExpectedCharge> expectedCharges(Lease lease) {
        Map<UUID, SplitRule> ruleByAccount = splitRules.findAllByUnitId(lease.getUnitId()).stream()
                .collect(Collectors.toMap(SplitRule::getUtilityAccountId, Function.identity(), (a, b) -> a));
        if (ruleByAccount.isEmpty()) {
            return List.of();
        }
        Set<UUID> alreadyCharged = new HashSet<>(lines.findBillIdsOnLockedInvoices(lease.getId()));
        LocalDate leaseEnd = lease.getEndDate() == null ? OPEN_ENDED : lease.getEndDate();

        List<ExpectedCharge> expected = new ArrayList<>();
        for (Bill bill : bills.findVerifiedOverlapping(lease.getLandlordId(), List.copyOf(ruleByAccount.keySet()),
                lease.getStartDate(), leaseEnd)) {
            if (alreadyCharged.contains(bill.getId()) || bill.getSplitAmount() == null) {
                continue;
            }
            SplitRule rule = ruleByAccount.get(bill.getUtilityAccountId());
            ChargeCalculator.calculate(bill.getPeriodStart(), bill.getPeriodEnd(), bill.getSplitAmount(),
                    lease.getStartDate(), lease.getEndDate(), rule.getShareType(), rule.getShareValue())
                    .ifPresent(charge -> expected.add(new ExpectedCharge(bill.getId(), rule, charge)));
        }
        return expected;
    }

    private Invoice createOpenInvoice(Lease lease) {
        LocalDate today = LocalDate.now(clock);
        Invoice invoice = new Invoice(lease.getLandlordId(), numbers.next(lease.getLandlordId(), today.getYear()),
                lease.getId(), lease.getUnitId(), null, null);
        invoice.setDueDate(today.plusDays(DUE_IN_DAYS));
        return invoices.save(invoice);
    }
}
