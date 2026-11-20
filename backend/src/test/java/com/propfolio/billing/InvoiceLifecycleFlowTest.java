package com.propfolio.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.propfolio.documents.Bill;
import com.propfolio.documents.BillRepository;
import com.propfolio.documents.BillStatus;

/**
 * Phase 5B through HTTP: Create invoice, Delete (undo), payments, Cancel, Archive, balances, PDF.
 * The Gmail steps (draft, sent) arrive in Phase 6, so tests call the lifecycle service for them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InvoiceLifecycleFlowTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private BillRepository bills;
    @Autowired private InvoiceLifecycleService lifecycle;

    private final UUID aliceId = UUID.randomUUID();
    private final RequestPostProcessor alice = landlord(aliceId);
    private final RequestPostProcessor bob = landlord(UUID.randomUUID());
    private final int year = LocalDate.now(ZoneId.of("America/Toronto")).getYear();
    private final String today = LocalDate.now(ZoneId.of("America/Toronto")).toString();

    @Test
    void threeBillScenarioFromOpenToArchived() throws Exception {
        House h = house();
        String tenant = tenant("Pat Tenant");
        lease(h.unit, tenant, "2026-01-01");
        verify(bill(h.gas, "2026-09-01", "2026-09-30", "20.00"));
        verify(bill(h.electricity, "2026-09-01", "2026-09-30", "30.00"));
        verify(bill(h.water, "2026-09-01", "2026-09-30", "10.00"));

        String id = onlyInvoice("OPEN");
        invoice(id).andExpect(jsonPath("$.invoice.totalAmount").value(60.00))
                .andExpect(jsonPath("$.invoice.actions", Matchers.contains("CREATE_INVOICE", "EDIT_DUE_DATE")));

        perform(post("/api/invoices/" + id + "/create")).andExpect(status().isOk())
                .andExpect(jsonPath("$.invoice.status").value("INVOICE_CREATED"))
                .andExpect(jsonPath("$.invoice.invoiceNumber").value("INV-%d-0001".formatted(year)))
                .andExpect(jsonPath("$.invoice.invoiceCreatedAt").exists())
                .andExpect(jsonPath("$.invoice.actions", Matchers.contains("DELETE", "CREATE_EMAIL_DRAFT", "EDIT_DUE_DATE")));

        draftAndSend(id);
        invoice(id).andExpect(jsonPath("$.invoice.status").value("SENT"))
                .andExpect(jsonPath("$.invoice.actions", Matchers.contains("RECORD_PAYMENT", "CANCEL")));

        pay(id, "35.00").andExpect(status().isCreated())
                .andExpect(jsonPath("$.invoice.status").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.invoice.received").value(35.00))
                .andExpect(jsonPath("$.invoice.remaining").value(25.00));
        pay(id, "25.00").andExpect(status().isCreated())
                .andExpect(jsonPath("$.invoice.status").value("PAID"))
                .andExpect(jsonPath("$.invoice.remaining").value(0))
                .andExpect(jsonPath("$.payments.length()").value(2));

        perform(post("/api/invoices/" + id + "/archive")).andExpect(status().isOk())
                .andExpect(jsonPath("$.invoice.status").value("ARCHIVED"));
        perform(get("/api/invoices")).andExpect(jsonPath("$.length()").value(0));
        perform(get("/api/invoices").param("includeArchived", "true")).andExpect(jsonPath("$.length()").value(1));
        perform(post("/api/invoices/" + id + "/unarchive")).andExpect(status().isOk())
                .andExpect(jsonPath("$.invoice.status").value("PAID"));
    }

    @Test
    void paymentsAreCheckedAgainstWhatsOwedAndTheStatus() throws Exception {
        House h = house();
        lease(h.unit, tenant("T"), "2026-01-01");
        verify(bill(h.gas, "2026-09-01", "2026-09-30", "20.00"));
        String id = onlyInvoice("OPEN");

        pay(id, "5.00").andExpect(status().isConflict()); // OPEN: not sent yet
        perform(post("/api/invoices/" + id + "/create"));
        pay(id, "5.00").andExpect(status().isConflict()); // created, not sent
        draftAndSend(id);

        pay(id, "20.01").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("amount"))
                .andExpect(jsonPath("$.errors[0].message").value(Matchers.containsString("20.00")));
        pay(id, "0").andExpect(status().isBadRequest());
        perform(post("/api/invoices/" + id + "/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 5, \"paidOn\": \"2999-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("paidOn"));

        pay(id, "20.00").andExpect(jsonPath("$.invoice.status").value("PAID"));
        pay(id, "1.00").andExpect(status().isConflict()); // paid: nothing more to record
    }

    @Test
    void deletingAPaymentRecalculatesTheStatus() throws Exception {
        House h = house();
        lease(h.unit, tenant("T"), "2026-01-01");
        verify(bill(h.gas, "2026-09-01", "2026-09-30", "20.00"));
        String id = sentInvoice();

        String paymentId = JsonPath.read(pay(id, "20.00").andReturn().getResponse().getContentAsString(), "$.payments[0].id");
        perform(delete("/api/payments/" + paymentId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.invoice.status").value("SENT"))
                .andExpect(jsonPath("$.invoice.received").value(0));
    }

    @Test
    void deleteBeforeSentUndoesCreateKeepsTheNumberAndMergesNewerCharges() throws Exception {
        House h = house();
        String leaseId = lease(h.unit, tenant("T"), "2026-01-01");
        verify(bill(h.gas, "2026-09-01", "2026-09-30", "20.00"));
        String first = onlyInvoice("OPEN");
        perform(post("/api/invoices/" + first + "/create"));

        verify(bill(h.electricity, "2026-09-01", "2026-09-30", "30.00")); // starts a newer OPEN invoice
        String newer = onlyInvoice("OPEN");
        assertThat(newer).isNotEqualTo(first);
        invoice(newer).andExpect(jsonPath("$.invoice.invoiceNumber").value("INV-%d-0002".formatted(year)));

        perform(delete("/api/invoices/" + first)).andExpect(status().isOk())
                .andExpect(jsonPath("$.invoice.status").value("OPEN"))
                .andExpect(jsonPath("$.invoice.invoiceNumber").value("INV-%d-0001".formatted(year)))
                .andExpect(jsonPath("$.invoice.invoiceCreatedAt").doesNotExist())
                .andExpect(jsonPath("$.invoice.totalAmount").value(50.00))
                .andExpect(jsonPath("$.charges.length()").value(2));
        perform(get("/api/invoices/" + newer)).andExpect(status().isNotFound());
        assertThat(onlyInvoice("OPEN")).isEqualTo(first);
        assertThat(leaseId).isNotNull();

        // From SENT on there is no Delete.
        draftAndSend(createAgain(first));
        perform(delete("/api/invoices/" + first)).andExpect(status().isConflict());
    }

    @Test
    void cancelIsBlockedWhilePaymentsExistAndFreesTheChargesOntoTheOpenInvoice() throws Exception {
        House h = house();
        lease(h.unit, tenant("T"), "2026-01-01");
        verify(bill(h.gas, "2026-09-01", "2026-09-30", "20.00"));
        String id = sentInvoice();

        String paymentId = JsonPath.read(pay(id, "10.00").andReturn().getResponse().getContentAsString(), "$.payments[0].id");
        perform(post("/api/invoices/" + id + "/cancel")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(Matchers.containsString("Delete its payments first")));
        perform(delete("/api/payments/" + paymentId));

        perform(post("/api/invoices/" + id + "/cancel")).andExpect(status().isOk())
                .andExpect(jsonPath("$.invoice.status").value("CANCELLED"))
                .andExpect(jsonPath("$.invoice.cancelledAt").exists())
                .andExpect(jsonPath("$.invoice.remaining").value(0))
                .andExpect(jsonPath("$.invoice.actions.length()").value(0))
                .andExpect(jsonPath("$.charges.length()").value(1)); // kept for the record

        String open = onlyInvoice("OPEN");
        invoice(open).andExpect(jsonPath("$.invoice.totalAmount").value(20.00))
                .andExpect(jsonPath("$.invoice.invoiceNumber").value("INV-%d-0002".formatted(year)));
        perform(get("/api/balances/tenants")).andExpect(jsonPath("$[0].outstanding").value(0))
                .andExpect(jsonPath("$[0].nextInvoice").value(20.00));
    }

    @Test
    void archiveOnlyFromPaid() throws Exception {
        House h = house();
        lease(h.unit, tenant("T"), "2026-01-01");
        verify(bill(h.gas, "2026-09-01", "2026-09-30", "20.00"));
        String id = onlyInvoice("OPEN");
        perform(post("/api/invoices/" + id + "/archive")).andExpect(status().isConflict());
        perform(post("/api/invoices/" + id + "/create"));
        perform(post("/api/invoices/" + id + "/archive")).andExpect(status().isConflict());
        draftAndSend(id);
        perform(post("/api/invoices/" + id + "/archive")).andExpect(status().isConflict());
        perform(post("/api/invoices/" + id + "/unarchive")).andExpect(status().isConflict());
    }

    @Test
    void outstandingAddsUpAcrossInvoicesAndOtherUnpaidShowsOnTheInvoice() throws Exception {
        House h = house();
        lease(h.unit, tenant("Owing Tenant"), "2026-01-01");
        verify(bill(h.gas, "2026-09-01", "2026-09-30", "60.00"));
        String first = sentInvoice();
        pay(first, "35.00");

        verify(bill(h.electricity, "2026-09-01", "2026-09-30", "30.00"));
        String second = onlyInvoice("OPEN");
        perform(post("/api/invoices/" + second + "/create")); // counts as owed from INVOICE_CREATED
        verify(bill(h.water, "2026-09-01", "2026-09-30", "10.00")); // collecting on a third, not owed yet

        perform(get("/api/balances/tenants"))
                .andExpect(jsonPath("$[0].tenantName").value("Owing Tenant"))
                .andExpect(jsonPath("$[0].outstanding").value(55.00)) // 25 left on the first + 30
                .andExpect(jsonPath("$[0].nextInvoice").value(10.00))
                .andExpect(jsonPath("$[0].unpaidInvoices.length()").value(2));

        invoice(second)
                .andExpect(jsonPath("$.invoice.totalAmount").value(30.00)) // its own charges only
                .andExpect(jsonPath("$.otherUnpaid.length()").value(1))
                .andExpect(jsonPath("$.otherUnpaid[0].invoiceNumber").value("INV-%d-0001".formatted(year)))
                .andExpect(jsonPath("$.otherUnpaid[0].remaining").value(25.00))
                .andExpect(jsonPath("$.totalOwing").value(55.00));
    }

    @Test
    void pdfShowsTheInvoice() throws Exception {
        House h = house();
        lease(h.unit, tenant("Pdf Tenant"), "2026-01-01");
        verify(bill(h.gas, "2026-09-01", "2026-09-30", "20.00"));
        String id = onlyInvoice("OPEN");
        perform(post("/api/invoices/" + id + "/create"));

        byte[] pdf = perform(get("/api/invoices/" + id + "/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", Matchers.containsString("inline")))
                .andReturn().getResponse().getContentAsByteArray();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("INV-%d-0001".formatted(year), "Pdf Tenant", "Enbridge", "$20.00");
        }
    }

    @Test
    void anotherLandlordCantSeeOrChangeTheInvoice() throws Exception {
        House h = house();
        lease(h.unit, tenant("T"), "2026-01-01");
        verify(bill(h.gas, "2026-09-01", "2026-09-30", "20.00"));
        String id = sentInvoice();
        String paymentId = JsonPath.read(pay(id, "5.00").andReturn().getResponse().getContentAsString(), "$.payments[0].id");

        for (var request : List.of(post("/api/invoices/" + id + "/create"), delete("/api/invoices/" + id),
                post("/api/invoices/" + id + "/cancel"), post("/api/invoices/" + id + "/archive"),
                get("/api/invoices/" + id + "/pdf"), delete("/api/payments/" + paymentId))) {
            mockMvc.perform(request.with(bob)).andExpect(status().isNotFound());
        }
        mockMvc.perform(post("/api/invoices/" + id + "/payments").with(bob).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 1, \"paidOn\": \"%s\"}".formatted(today)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/balances/tenants").with(bob)).andExpect(jsonPath("$.length()").value(0));
        invoice(id).andExpect(jsonPath("$.invoice.received").value(5.00));
    }

    // ---- helpers ------------------------------------------------------------------------------

    /** Alice's house: one unit paying 100% of gas, electricity and water. */
    private record House(String property, String unit, String gas, String electricity, String water) {
    }

    private House house() throws Exception {
        String property = create("/api/properties", "{\"name\": \"House %s\"}".formatted(UUID.randomUUID()));
        String unit = create("/api/properties/" + property + "/units", "{\"name\": \"Main\"}");
        String gas = account(property, "GAS", "Enbridge");
        String electricity = account(property, "ELECTRICITY", "Alectra");
        String water = account(property, "WATER", "Region Water");
        for (String account : List.of(gas, electricity, water)) {
            perform(put("/api/split-rules").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"unitId\": \"%s\", \"utilityAccountId\": \"%s\", \"shareType\": \"PERCENT\", \"shareValue\": 100}"
                            .formatted(unit, account)))
                    .andExpect(status().isOk());
        }
        return new House(property, unit, gas, electricity, water);
    }

    private String account(String property, String type, String provider) throws Exception {
        return create("/api/properties/" + property + "/utility-accounts",
                "{\"utilityType\": \"%s\", \"providerName\": \"%s\"}".formatted(type, provider));
    }

    private String tenant(String name) throws Exception {
        return create("/api/tenants", "{\"fullName\": \"%s\", \"emails\": [\"t%s@example.com\"]}"
                .formatted(name, UUID.randomUUID().toString().substring(0, 8)));
    }

    private String lease(String unit, String tenant, String start) throws Exception {
        return create("/api/units/" + unit + "/leases",
                "{\"tenantId\": \"%s\", \"startDate\": \"%s\", \"endDate\": null}".formatted(tenant, start));
    }

    private String bill(String accountId, String start, String end, String amount) {
        Bill bill = new Bill(aliceId, "bill.pdf", aliceId + "/x/" + UUID.randomUUID() + ".pdf", "application/pdf", 10,
                "sha-" + UUID.randomUUID());
        bill.setUtilityAccountId(UUID.fromString(accountId));
        bill.setPeriodStart(LocalDate.parse(start));
        bill.setPeriodEnd(LocalDate.parse(end));
        bill.setCurrentCharges(new BigDecimal(amount));
        bill.setAmountDue(new BigDecimal(amount));
        bill.setStatus(BillStatus.READY);
        return bills.save(bill).getId().toString();
    }

    private void verify(String billId) throws Exception {
        perform(post("/api/bills/" + billId + "/verify")).andExpect(status().isOk());
    }

    /** The only invoice of Alice with this status. */
    private String onlyInvoice(String status) throws Exception {
        String json = perform(get("/api/invoices").param("status", status))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$[0].id");
    }

    /** Creates the OPEN invoice and takes it through the Phase 6 steps to SENT. */
    private String sentInvoice() throws Exception {
        String id = onlyInvoice("OPEN");
        perform(post("/api/invoices/" + id + "/create")).andExpect(status().isOk());
        draftAndSend(id);
        return id;
    }

    private String createAgain(String id) throws Exception {
        perform(post("/api/invoices/" + id + "/create")).andExpect(status().isOk());
        return id;
    }

    /** Phase 6 will do these through Gmail; here, as Alice, straight through the lifecycle service. */
    private void draftAndSend(String id) {
        Jwt token = Jwt.withTokenValue("test").header("alg", "none").subject(aliceId.toString())
                .claim("email", aliceId + "@example.com").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token));
        try {
            lifecycle.markEmailDrafted(UUID.fromString(id), "draft-1");
            lifecycle.markSent(UUID.fromString(id));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private ResultActions pay(String invoiceId, String amount) throws Exception {
        return perform(post("/api/invoices/" + invoiceId + "/payments").contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": %s, \"paidOn\": \"%s\", \"note\": \"e-transfer\"}".formatted(amount, today)));
    }

    private ResultActions invoice(String invoiceId) throws Exception {
        return perform(get("/api/invoices/" + invoiceId)).andExpect(status().isOk());
    }

    private ResultActions perform(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        return mockMvc.perform(request.with(alice));
    }

    private String create(String path, String body) throws Exception {
        String json = perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private static RequestPostProcessor landlord(UUID id) {
        return jwt().jwt(token -> token.subject(id.toString()).claim("email", id + "@example.com"));
    }
}
