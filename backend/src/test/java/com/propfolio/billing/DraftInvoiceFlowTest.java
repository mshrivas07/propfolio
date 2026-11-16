package com.propfolio.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.propfolio.documents.Bill;
import com.propfolio.documents.BillRepository;
import com.propfolio.documents.BillStatus;
import com.propfolio.rentals.LeaseRepository;

/**
 * Verified bills → charges on each lease's OPEN invoice, through HTTP. Bills are inserted as READY
 * (already read) and verified through the API, which is what triggers the invoices.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DraftInvoiceFlowTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private BillRepository bills;
    @Autowired private InvoiceRepository invoices;
    @Autowired private InvoiceLineRepository invoiceLines;
    @Autowired private LeaseRepository leases;
    @Autowired private DraftInvoiceService draftInvoices;

    private final UUID aliceId = UUID.randomUUID();
    private final RequestPostProcessor alice = landlord(aliceId);
    private final RequestPostProcessor bob = landlord(UUID.randomUUID());
    private final int year = LocalDate.now(ZoneId.of("America/Toronto")).getYear();

    @Test
    void verifiedBillsBecomeChargesOnOneOpenInvoice() throws Exception {
        House h = house("70");
        String tenant = tenant(alice, "Van Scott Tenant");
        lease(alice, h.unit, tenant, "2026-09-01", null);

        verify(bill(h.gas, "2026-08-26", "2026-09-23", "124.00", "124.00"));
        String invoiceId = onlyOpenInvoice();

        invoice(invoiceId)
                .andExpect(jsonPath("$.invoice.status").value("OPEN"))
                .andExpect(jsonPath("$.invoice.invoiceNumber").value("INV-%d-0001".formatted(year)))
                .andExpect(jsonPath("$.invoice.totalAmount").value(68.84))
                .andExpect(jsonPath("$.invoice.tenantName").value("Van Scott Tenant"))
                .andExpect(jsonPath("$.invoice.propertyId").value(h.property))
                .andExpect(jsonPath("$.charges[0].daysCovered").value(23))
                .andExpect(jsonPath("$.charges[0].billPeriodDays").value(29))
                .andExpect(jsonPath("$.charges[0].providerName").value("Enbridge"));

        verify(bill(h.internet, "2026-08-22", "2026-09-21", "90.40", "90.40"));

        invoice(invoiceId)
                .andExpect(jsonPath("$.invoice.totalAmount").value(111.71)) // 68.84 + 42.87
                .andExpect(jsonPath("$.invoice.chargeCount").value(2))
                .andExpect(jsonPath("$.invoice.periodStart").value("2026-09-01"))
                .andExpect(jsonPath("$.invoice.periodEnd").value("2026-09-23"));
        assertThat(invoices.findAll().stream().filter(i -> i.getLandlordId().equals(aliceId)).count()).isEqualTo(1);
    }

    @Test
    void unVerifyingRemovesTheChargeAndKeepsTheEmptyInvoiceAndItsNumber() throws Exception {
        House h = house("70");
        lease(alice, h.unit, tenant(alice, "T"), "2026-09-01", null);
        String billId = bill(h.gas, "2026-09-01", "2026-09-30", "100.00", "100.00");
        verify(billId);
        String invoiceId = onlyOpenInvoice();

        mockMvc.perform(post("/api/bills/" + billId + "/unverify").with(alice)).andExpect(status().isOk());
        invoice(invoiceId)
                .andExpect(jsonPath("$.invoice.totalAmount").value(0))
                .andExpect(jsonPath("$.invoice.periodStart").doesNotExist())
                .andExpect(jsonPath("$.charges.length()").value(0));

        verify(billId);
        assertThat(onlyOpenInvoice()).isEqualTo(invoiceId);
        invoice(invoiceId).andExpect(jsonPath("$.invoice.invoiceNumber").value("INV-%d-0001".formatted(year)));
    }

    @Test
    void changingTheSplitRuleRecalculatesTheOpenInvoice() throws Exception {
        House h = house("70");
        lease(alice, h.unit, tenant(alice, "T"), "2026-09-01", null);
        verify(bill(h.gas, "2026-09-01", "2026-09-30", "100.00", "100.00"));
        String invoiceId = onlyOpenInvoice();
        invoice(invoiceId).andExpect(jsonPath("$.invoice.totalAmount").value(70.00));

        rule(h.unit, h.gas, "PERCENT", "50");

        invoice(invoiceId)
                .andExpect(jsonPath("$.invoice.totalAmount").value(50.00))
                .andExpect(jsonPath("$.charges[0].shareValue").value(50));
    }

    @Test
    void changingTheLeaseEndRecalculatesTheOpenInvoice() throws Exception {
        House h = house("100");
        String leaseId = lease(alice, h.unit, tenant(alice, "T"), "2026-01-01", null);
        verify(bill(h.gas, "2026-09-01", "2026-09-30", "300.00", "300.00"));
        String invoiceId = onlyOpenInvoice();

        mockMvc.perform(put("/api/leases/" + leaseId).with(alice).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\": \"2026-01-01\", \"endDate\": \"2026-09-15\"}"))
                .andExpect(status().isOk());

        invoice(invoiceId)
                .andExpect(jsonPath("$.invoice.totalAmount").value(150.00))
                .andExpect(jsonPath("$.charges[0].daysCovered").value(15));
    }

    @Test
    void twoTenantsInOneBillPeriodEachPayForTheirOwnDays() throws Exception {
        House h = house("100");
        String first = lease(alice, h.unit, tenant(alice, "Moving out"), "2026-01-01", "2026-09-15");
        String second = lease(alice, h.unit, tenant(alice, "Moving in"), "2026-09-16", null);

        verify(bill(h.gas, "2026-09-01", "2026-09-30", "300.00", "300.00"));

        Invoice a = invoices.findFirstByLeaseIdAndStatus(UUID.fromString(first), InvoiceStatus.OPEN).orElseThrow();
        Invoice b = invoices.findFirstByLeaseIdAndStatus(UUID.fromString(second), InvoiceStatus.OPEN).orElseThrow();
        assertThat(a.getTotalAmount()).isEqualByComparingTo("150.00");
        assertThat(b.getTotalAmount()).isEqualByComparingTo("150.00");
        assertThat(List.of(a.getInvoiceNumber(), b.getInvoiceNumber()))
                .containsExactlyInAnyOrder("INV-%d-0001".formatted(year), "INV-%d-0002".formatted(year));
    }

    @Test
    void vacantUnitGetsNoInvoice() throws Exception {
        House h = house("70");
        verify(bill(h.gas, "2026-09-01", "2026-09-30", "100.00", "100.00"));

        mockMvc.perform(get("/api/invoices").with(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void equalPaymentPlanAccountSplitsThePlanPayment() throws Exception {
        House h = house("70");
        mockMvc.perform(put("/api/utility-accounts/" + h.gas).with(alice).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"utilityType\": \"GAS\", \"providerName\": \"Enbridge\", \"accountNumber\": \"G-1\", "
                                + "\"equalPaymentPlan\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equalPaymentPlan").value(true));
        lease(alice, h.unit, tenant(alice, "T"), "2026-01-01", null);

        // Actual usage $63.55, plan payment $100.29
        String billId = bill(h.gas, "2026-09-24", "2026-10-23", "63.55", "100.29");
        verify(billId)
                .andExpect(jsonPath("$.splitAmount").value(100.29))
                .andExpect(jsonPath("$.splitBasis").value("PLAN_PAYMENT"));

        invoice(onlyOpenInvoice()).andExpect(jsonPath("$.invoice.totalAmount").value(70.20));
    }

    @Test
    void flatAmountIsChargedPerBillAndProrated() throws Exception {
        House h = house("70");
        rule(h.unit, h.internet, "FLAT", "50");
        lease(alice, h.unit, tenant(alice, "T"), "2026-09-17", null);

        verify(bill(h.internet, "2026-09-02", "2026-10-01", "80.00", "80.00"));

        invoice(onlyOpenInvoice())
                .andExpect(jsonPath("$.invoice.totalAmount").value(25.00))
                .andExpect(jsonPath("$.charges[0].shareType").value("FLAT"));
    }

    @Test
    void aBillOnALockedInvoiceIsNeitherRechargedNorUnverifiable() throws Exception {
        House h = house("70");
        lease(alice, h.unit, tenant(alice, "T"), "2026-09-01", null);
        String billId = bill(h.gas, "2026-09-01", "2026-09-30", "100.00", "100.00");
        verify(billId);
        Invoice locked = invoices.findById(UUID.fromString(onlyOpenInvoice())).orElseThrow();
        locked.setStatus(InvoiceStatus.EMAIL_DRAFTED); // stands in for Phase 5B's "Create invoice"
        invoices.save(locked);

        rule(h.unit, h.gas, "PERCENT", "50"); // triggers a rebuild

        assertThat(invoices.findFirstByLeaseIdAndStatus(locked.getLeaseId(), InvoiceStatus.OPEN)).isEmpty();
        assertThat(invoices.findById(locked.getId()).orElseThrow().getTotalAmount()).isEqualByComparingTo("70.00");
        mockMvc.perform(post("/api/bills/" + billId + "/unverify").with(alice))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("no longer open")));
    }

    @Test
    void rebuildingTwiceChangesNothing() throws Exception {
        House h = house("70");
        String leaseId = lease(alice, h.unit, tenant(alice, "T"), "2026-09-01", null);
        verify(bill(h.gas, "2026-09-01", "2026-09-30", "100.00", "100.00"));
        verify(bill(h.internet, "2026-09-05", "2026-10-04", "60.00", "60.00"));
        UUID invoiceId = UUID.fromString(onlyOpenInvoice());
        List<UUID> before = invoiceLines.findAllByInvoiceIdOrderByCoveredStartAsc(invoiceId).stream()
                .map(InvoiceLine::getId).toList();

        draftInvoices.rebuildLease(leases.findById(UUID.fromString(leaseId)).orElseThrow());
        draftInvoices.rebuildLease(leases.findById(UUID.fromString(leaseId)).orElseThrow());

        assertThat(invoiceLines.findAllByInvoiceIdOrderByCoveredStartAsc(invoiceId).stream().map(InvoiceLine::getId))
                .containsExactlyElementsOf(before);
        assertThat(invoices.findById(invoiceId).orElseThrow().getTotalAmount()).isEqualByComparingTo("112.00");
    }

    @Test
    void dueDateDefaultsTo15DaysAndCanBeChangedWhileOpen() throws Exception {
        House h = house("70");
        lease(alice, h.unit, tenant(alice, "T"), "2026-09-01", null);
        verify(bill(h.gas, "2026-09-01", "2026-09-30", "100.00", "100.00"));
        String invoiceId = onlyOpenInvoice();
        LocalDate expected = LocalDate.now(ZoneId.of("America/Toronto")).plusDays(15);

        invoice(invoiceId).andExpect(jsonPath("$.invoice.dueDate").value(expected.toString()));
        mockMvc.perform(put("/api/invoices/" + invoiceId + "/due-date").with(alice)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dueDate\": \"2026-12-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoice.dueDate").value("2026-12-01"));

        Invoice locked = invoices.findById(UUID.fromString(invoiceId)).orElseThrow();
        locked.setStatus(InvoiceStatus.EMAIL_DRAFTED);
        invoices.save(locked);
        mockMvc.perform(put("/api/invoices/" + invoiceId + "/due-date").with(alice)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dueDate\": \"2026-12-05\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void aLeaseWithOnlyAnOpenInvoiceCanBeDeletedButNotOnceInvoiced() throws Exception {
        House h = house("70");
        String leaseId = lease(alice, h.unit, tenant(alice, "T"), "2026-09-01", null);
        verify(bill(h.gas, "2026-09-01", "2026-09-30", "100.00", "100.00"));
        String invoiceId = onlyOpenInvoice();

        mockMvc.perform(delete("/api/leases/" + leaseId).with(alice)).andExpect(status().isNoContent());
        assertThat(invoices.findById(UUID.fromString(invoiceId))).isEmpty();

        String second = lease(alice, h.unit, tenant(alice, "T2"), "2026-09-01", null);
        Invoice locked = invoices.findFirstByLeaseIdAndStatus(UUID.fromString(second), InvoiceStatus.OPEN).orElseThrow();
        locked.setStatus(InvoiceStatus.EMAIL_DRAFTED);
        invoices.save(locked);
        mockMvc.perform(delete("/api/leases/" + second).with(alice)).andExpect(status().isConflict());
    }

    @Test
    void numbersAreSeparatePerLandlordAndInvoicesArePrivate() throws Exception {
        House h = house("70");
        lease(alice, h.unit, tenant(alice, "T"), "2026-09-01", null);
        verify(bill(h.gas, "2026-09-01", "2026-09-30", "100.00", "100.00"));
        String invoiceId = onlyOpenInvoice();

        mockMvc.perform(get("/api/invoices/" + invoiceId).with(bob)).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/invoices/" + invoiceId + "/due-date").with(bob)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dueDate\": \"2026-12-01\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/invoices").with(bob)).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/invoices").param("status", "OPEN").param("propertyId", h.property).with(alice))
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ---- helpers ------------------------------------------------------------------------------

    /** Alice's house: one unit, Enbridge gas and Rogers internet, both split at {@code percent}. */
    private record House(String property, String unit, String gas, String internet) {
    }

    private House house(String percent) throws Exception {
        String property = create(alice, "/api/properties", "{\"name\": \"House %s\"}".formatted(UUID.randomUUID()));
        String unit = create(alice, "/api/properties/" + property + "/units", "{\"name\": \"Main\"}");
        String gas = create(alice, "/api/properties/" + property + "/utility-accounts",
                "{\"utilityType\": \"GAS\", \"providerName\": \"Enbridge\", \"accountNumber\": \"G-1\"}");
        String internet = create(alice, "/api/properties/" + property + "/utility-accounts",
                "{\"utilityType\": \"INTERNET\", \"providerName\": \"Rogers\", \"accountNumber\": \"R-1\"}");
        rule(unit, gas, "PERCENT", percent);
        rule(unit, internet, "PERCENT", percent);
        return new House(property, unit, gas, internet);
    }

    private void rule(String unit, String account, String type, String value) throws Exception {
        mockMvc.perform(put("/api/split-rules").with(alice).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unitId\": \"%s\", \"utilityAccountId\": \"%s\", \"shareType\": \"%s\", \"shareValue\": %s}"
                                .formatted(unit, account, type, value)))
                .andExpect(status().isOk());
    }

    private String tenant(RequestPostProcessor who, String name) throws Exception {
        return create(who, "/api/tenants", "{\"fullName\": \"%s\", \"emails\": [\"t%s@example.com\"]}"
                .formatted(name, UUID.randomUUID().toString().substring(0, 8)));
    }

    private String lease(RequestPostProcessor who, String unit, String tenant, String start, String end) throws Exception {
        return create(who, "/api/units/" + unit + "/leases", "{\"tenantId\": \"%s\", \"startDate\": \"%s\", \"endDate\": %s}"
                .formatted(tenant, start, end == null ? "null" : "\"" + end + "\""));
    }

    /** A bill that has been read already (READY), linked to the account. */
    private String bill(String accountId, String start, String end, String currentCharges, String amountDue) {
        Bill bill = new Bill(aliceId, "bill.pdf", aliceId + "/x/" + UUID.randomUUID() + ".pdf", "application/pdf", 10,
                "sha-" + UUID.randomUUID());
        bill.setUtilityAccountId(UUID.fromString(accountId));
        bill.setProviderName("Provider");
        bill.setPeriodStart(LocalDate.parse(start));
        bill.setPeriodEnd(LocalDate.parse(end));
        bill.setCurrentCharges(new BigDecimal(currentCharges));
        bill.setAmountDue(new BigDecimal(amountDue));
        bill.setStatus(BillStatus.READY);
        return bills.save(bill).getId().toString();
    }

    private ResultActions verify(String billId) throws Exception {
        return mockMvc.perform(post("/api/bills/" + billId + "/verify").with(alice)).andExpect(status().isOk());
    }

    private String onlyOpenInvoice() throws Exception {
        String json = mockMvc.perform(get("/api/invoices").param("status", "OPEN").with(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$[0].id");
    }

    private ResultActions invoice(String invoiceId) throws Exception {
        return mockMvc.perform(get("/api/invoices/" + invoiceId).with(alice)).andExpect(status().isOk());
    }

    private String create(RequestPostProcessor who, String path, String body) throws Exception {
        String json = mockMvc.perform(post(path).with(who).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private static RequestPostProcessor landlord(UUID id) {
        return jwt().jwt(token -> token.subject(id.toString()).claim("email", id + "@example.com"));
    }
}
