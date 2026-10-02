package com.propfolio.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.propfolio.billing.Invoice;
import com.propfolio.billing.InvoiceLine;
import com.propfolio.billing.InvoiceLineRepository;
import com.propfolio.billing.InvoiceNumberSequence;
import com.propfolio.billing.InvoiceNumberSequenceRepository;
import com.propfolio.billing.InvoiceRepository;
import com.propfolio.billing.InvoiceStatus;
import com.propfolio.documents.Bill;
import com.propfolio.documents.BillRepository;
import com.propfolio.documents.BillStatus;
import com.propfolio.payments.Payment;
import com.propfolio.payments.PaymentRepository;
import com.propfolio.rentals.Lease;
import com.propfolio.rentals.LeaseRepository;
import com.propfolio.rentals.Property;
import com.propfolio.rentals.PropertyRepository;
import com.propfolio.rentals.ShareType;
import com.propfolio.rentals.SplitRule;
import com.propfolio.rentals.SplitRuleRepository;
import com.propfolio.rentals.Tenant;
import com.propfolio.rentals.TenantRepository;
import com.propfolio.rentals.Unit;
import com.propfolio.rentals.UnitRepository;
import com.propfolio.rentals.UtilityAccount;
import com.propfolio.rentals.UtilityAccountRepository;
import com.propfolio.rentals.UtilityType;
import com.propfolio.security.Landlord;
import com.propfolio.security.LandlordRepository;

/**
 * Saves and re-reads one row in every table, so mapping mistakes (column names,
 * arrays, JSON, enums, decimals) show up before any business code depends on them.
 * Rolled back after the test.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RepositorySmokeTest {

    @Autowired private EntityManager entityManager;
    @Autowired private LandlordRepository landlords;
    @Autowired private PropertyRepository properties;
    @Autowired private UnitRepository units;
    @Autowired private TenantRepository tenants;
    @Autowired private LeaseRepository leases;
    @Autowired private UtilityAccountRepository utilityAccounts;
    @Autowired private SplitRuleRepository splitRules;
    @Autowired private BillRepository bills;
    @Autowired private InvoiceRepository invoices;
    @Autowired private InvoiceLineRepository invoiceLines;
    @Autowired private InvoiceNumberSequenceRepository sequences;
    @Autowired private PaymentRepository payments;

    @Test
    void everyEntityRoundTrips() {
        UUID landlordId = UUID.randomUUID();
        landlords.save(new Landlord(landlordId, "owner@example.com"));

        Property property = properties.save(new Property(landlordId, "Test House"));
        Unit unit = units.save(new Unit(landlordId, property.getId(), "Main floor"));
        Tenant tenant = tenants.save(new Tenant(landlordId, "Test Tenant", List.of("a@example.com", "b@example.com")));
        Lease lease = leases.save(new Lease(landlordId, unit.getId(), tenant.getId(), LocalDate.of(2026, 1, 1)));
        UtilityAccount water = utilityAccounts.save(
                new UtilityAccount(landlordId, property.getId(), UtilityType.WATER, "City Water"));
        SplitRule rule = splitRules.save(
                new SplitRule(landlordId, unit.getId(), water.getId(), ShareType.PERCENT, new BigDecimal("70")));

        Bill bill = new Bill(landlordId, "water.pdf", "landlord/2026/01/water.pdf", "application/pdf", 1024L, "sha-256-hex");
        bill.setExtractionJson("{\"total\": 120.50}");
        bill.setAmountDue(new BigDecimal("120.50"));
        bill = bills.save(bill);

        Invoice invoice = invoices.save(new Invoice(landlordId, "INV-2026-0001", lease.getId(), unit.getId(),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)));
        InvoiceLine line = invoiceLines.save(new InvoiceLine(landlordId, invoice.getId(), bill.getId(), unit.getId(),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), 31, 90, ShareType.PERCENT,
                new BigDecimal("70"), new BigDecimal("29.17")));
        InvoiceNumberSequence sequence = sequences.save(new InvoiceNumberSequence(landlordId, 2026));
        Payment payment = payments.save(new Payment(landlordId, invoice.getId(), new BigDecimal("10.00"),
                LocalDate.of(2026, 2, 5)));

        entityManager.flush();
        entityManager.clear(); // force real reads from the database below

        assertThat(landlords.findById(landlordId)).get()
                .extracting(Landlord::getEmail).isEqualTo("owner@example.com");
        assertThat(tenants.findById(tenant.getId()).orElseThrow().getEmails())
                .containsExactly("a@example.com", "b@example.com");
        assertThat(splitRules.findById(rule.getId()).orElseThrow().getShareType()).isEqualTo(ShareType.PERCENT);

        Bill savedBill = bills.findById(bill.getId()).orElseThrow();
        assertThat(savedBill.getStatus()).isEqualTo(BillStatus.UPLOADED);
        assertThat(savedBill.getAmountDue()).isEqualByComparingTo("120.50");
        assertThat(savedBill.getExtractionJson()).contains("total");
        assertThat(savedBill.getCreatedAt()).isNotNull();

        Invoice savedInvoice = invoices.findById(invoice.getId()).orElseThrow();
        assertThat(savedInvoice.getStatus()).isEqualTo(InvoiceStatus.DRAFT);
        assertThat(savedInvoice.getTotalAmount()).isEqualByComparingTo("0");

        assertThat(invoiceLines.findById(line.getId()).orElseThrow().isVoided()).isFalse();
        assertThat(sequences.findById(sequence.getId()).orElseThrow().getNextValue()).isEqualTo(1);
        assertThat(payments.findById(payment.getId()).orElseThrow().getAmount()).isEqualByComparingTo("10.00");
        assertThat(leases.findById(lease.getId()).orElseThrow().getEndDate()).isNull();
    }
}
