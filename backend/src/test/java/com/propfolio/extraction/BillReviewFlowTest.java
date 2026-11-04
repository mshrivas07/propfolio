package com.propfolio.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.propfolio.billing.Invoice;
import com.propfolio.billing.InvoiceLine;
import com.propfolio.billing.InvoiceLineRepository;
import com.propfolio.billing.InvoiceRepository;
import com.propfolio.documents.Bill;
import com.propfolio.documents.BillRepository;
import com.propfolio.documents.BillReviewService;
import com.propfolio.documents.ExtractedBill;
import com.propfolio.documents.TestFiles;
import com.propfolio.documents.TestReadings;
import com.propfolio.rentals.ShareType;

/**
 * Upload → automatic reading → review → verify, through HTTP. OpenAI is replaced by a mock;
 * readings run on the request thread right after commit (app.extraction.inline=true).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.bills.auto-extract=true")
class BillReviewFlowTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private BillRepository bills;
    @Autowired private BillReviewService reviewService;
    @Autowired private InvoiceRepository invoices;
    @Autowired private InvoiceLineRepository invoiceLines;
    @MockitoBean private OpenAiBillReader reader;

    private final UUID aliceId = UUID.randomUUID();
    private final RequestPostProcessor alice = landlord(aliceId);
    private final RequestPostProcessor bob = landlord(UUID.randomUUID());

    @Test
    void uploadIsReadAutomaticallyMatchedAndReady() throws Exception {
        Setup s = setup("1234 5678-9");
        willRead(TestReadings.hydro("123456789")); // printed without spaces; still matches

        String billId = upload(alice, null);

        bill(alice, billId)
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.extractionState").value("DONE"))
                .andExpect(jsonPath("$.utilityAccountId").value(s.hydroId))
                .andExpect(jsonPath("$.propertyId").value(s.propertyId))
                .andExpect(jsonPath("$.currentCharges").value(120.00))
                .andExpect(jsonPath("$.billUtilityType").value("ELECTRICITY"))
                .andExpect(jsonPath("$.confidence").value(0.98))
                .andExpect(jsonPath("$.fieldReview.currentCharges.confidence").value(0.98))
                .andExpect(jsonPath("$.issues.length()").value(0));
    }

    @Test
    void chosenAccountWithADifferentNumberNeedsReviewAndIsNotRelinked() throws Exception {
        Setup s = setup("1234 5678-9");
        willRead(TestReadings.hydro("999-999"));

        String billId = upload(alice, s.hydroId);

        bill(alice, billId)
                .andExpect(jsonPath("$.status").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.utilityAccountId").value(s.hydroId))
                .andExpect(jsonPath("$.issues[*].field").value(hasItem("accountNumber")));
    }

    @Test
    void unknownAccountNumberNeedsReviewWithNoAccountLinked() throws Exception {
        setup("1234 5678-9");
        willRead(TestReadings.hydro("555"));

        String billId = upload(alice, null);

        bill(alice, billId)
                .andExpect(jsonPath("$.status").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.utilityAccountId").doesNotExist())
                .andExpect(jsonPath("$.issues[*].field").value(hasItem("utilityAccountId")));
    }

    @Test
    void failedReadingKeepsTheBillUploadedWithTheReason() throws Exception {
        setup("1");
        given(reader.read(any(byte[].class), anyString(), anyString()))
                .willThrow(new ExtractionException("OpenAI's rate limit or quota was reached."));

        String billId = upload(alice, null);

        bill(alice, billId)
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andExpect(jsonPath("$.extractionState").value("FAILED"))
                .andExpect(jsonPath("$.extractionError").value("OpenAI's rate limit or quota was reached."));
        mockMvc.perform(get("/api/bills").with(alice))
                .andExpect(jsonPath("$[0].extractionState").value("FAILED"));
    }

    @Test
    void failedBillCanBeFilledInByHandAndVerified() throws Exception {
        Setup s = setup("1234 5678-9");
        given(reader.read(any(byte[].class), anyString(), anyString())).willThrow(new ExtractionException("nope"));
        String billId = upload(alice, null);

        saveFields(alice, billId, s.hydroId, "2026-09-01", "2026-09-30", "120.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.fieldReview.currentCharges.edited").value(true));

        mockMvc.perform(post("/api/bills/" + billId + "/verify").with(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.verifiedAt").exists());
    }

    @Test
    void reRunReadsAgainAndReplacesTheFields() throws Exception {
        Setup s = setup("1234 5678-9");
        willRead(TestReadings.hydro("1234 5678-9"));
        String billId = upload(alice, null);

        willRead(TestReadings.reading("Sample Hydro Inc.", "1234 5678-9", "ELECTRICITY", "2026-09-01",
                "2026-09-30", "130.00", "0.00", "130.00", "2026-10-20", "0.97"));
        mockMvc.perform(post("/api/bills/" + billId + "/extract").with(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UPLOADED"));

        bill(alice, billId)
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.currentCharges").value(130.00))
                .andExpect(jsonPath("$.utilityAccountId").value(s.hydroId));
    }

    @Test
    void anOutdatedReadingIsIgnored() throws Exception {
        setup("1234 5678-9");
        willRead(TestReadings.hydro("1234 5678-9"));
        String billId = upload(alice, null);

        reviewService.applyExtraction(UUID.fromString(billId), Instant.parse("2020-01-01T00:00:00Z"),
                ExtractedBill.fromJson(TestReadings.hydro("other")), "{}");

        bill(alice, billId).andExpect(jsonPath("$.billAccountNumber").value("1234 5678-9"));
    }

    @Test
    void verifyNeedsAccountPeriodAndChargesAndListsEveryMissingField() throws Exception {
        setup("1");
        willRead(TestReadings.reading(null, null, null, null, null, null, null, null, null, "0.1"));
        String billId = upload(alice, null);

        mockMvc.perform(post("/api/bills/" + billId + "/verify").with(alice))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(4))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("utilityAccountId")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("periodStart")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("periodEnd")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("currentCharges")));
    }

    @Test
    void secondVerifiedBillForTheSameDaysIsBlocked() throws Exception {
        Setup s = setup("1234 5678-9");
        willRead(TestReadings.hydro("1234 5678-9"));
        String first = upload(alice, null);
        mockMvc.perform(post("/api/bills/" + first + "/verify").with(alice)).andExpect(status().isOk());

        willRead(TestReadings.hydro("1234 5678-9").replace("120.00", "121.00"));
        String second = upload(alice, null, TestFiles.pdf("second-copy-" + UUID.randomUUID()));

        bill(alice, second)
                .andExpect(jsonPath("$.status").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.issues[*].field").value(hasItem("periodStart")));
        mockMvc.perform(post("/api/bills/" + second + "/verify").with(alice))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("charged twice")));
        assertThat(s.hydroId).isNotNull();
    }

    @Test
    void verifiedBillIsLockedUntilUnverified() throws Exception {
        Setup s = setup("1234 5678-9");
        willRead(TestReadings.hydro("1234 5678-9"));
        String billId = upload(alice, null);
        mockMvc.perform(post("/api/bills/" + billId + "/verify").with(alice)).andExpect(status().isOk());

        saveFields(alice, billId, s.hydroId, "2026-09-01", "2026-09-30", "99.00").andExpect(status().isConflict());
        mockMvc.perform(post("/api/bills/" + billId + "/extract").with(alice)).andExpect(status().isConflict());

        mockMvc.perform(post("/api/bills/" + billId + "/unverify").with(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.verifiedAt").doesNotExist());
        saveFields(alice, billId, s.hydroId, "2026-09-01", "2026-09-30", "99.00").andExpect(status().isOk());
    }

    @Test
    void billOnALiveInvoiceCannotBeUnverifiedButAVoidedOneCan() throws Exception {
        setup("1234 5678-9");
        willRead(TestReadings.hydro("1234 5678-9"));
        String billId = upload(alice, null);
        mockMvc.perform(post("/api/bills/" + billId + "/verify").with(alice)).andExpect(status().isOk());

        UUID unitId = UUID.randomUUID();
        Invoice invoice = invoices.save(new Invoice(aliceId, "INV-T-" + UUID.randomUUID(), UUID.randomUUID(), unitId,
                LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30")));
        InvoiceLine line = invoiceLines.save(new InvoiceLine(aliceId, invoice.getId(), UUID.fromString(billId), unitId,
                LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30"), 30, 30, ShareType.PERCENT,
                new BigDecimal("70"), new BigDecimal("84.00")));

        mockMvc.perform(post("/api/bills/" + billId + "/unverify").with(alice))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Void that invoice")));

        line.setVoided(true);
        invoiceLines.save(line);
        mockMvc.perform(post("/api/bills/" + billId + "/unverify").with(alice)).andExpect(status().isOk());
    }

    @Test
    void anotherLandlordsAccountCannotBeLinkedDuringReview() throws Exception {
        setup("1");
        given(reader.read(any(byte[].class), anyString(), anyString())).willThrow(new ExtractionException("nope"));
        String billId = upload(alice, null);
        String bobsAccount = createAccount(bob, "Bob House " + UUID.randomUUID(), "77");

        saveFields(alice, billId, bobsAccount, "2026-09-01", "2026-09-30", "120.00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("utilityAccountId"));
    }

    @Test
    void anotherLandlordCannotReviewTheBill() throws Exception {
        setup("1234 5678-9");
        willRead(TestReadings.hydro("1234 5678-9"));
        String billId = upload(alice, null);

        bill(bob, billId).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/bills/" + billId + "/extract").with(bob)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/bills/" + billId + "/verify").with(bob)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/bills/" + billId + "/unverify").with(bob)).andExpect(status().isNotFound());
        saveFields(bob, billId, null, "2026-09-01", "2026-09-30", "1.00").andExpect(status().isNotFound());

        Bill bill = bills.findById(UUID.fromString(billId)).orElseThrow();
        assertThat(bill.getCurrentCharges()).isEqualByComparingTo("120.00");
    }

    // ---- helpers ----------------------------------------------------------------------------

    private record Setup(String propertyId, String hydroId) {
    }

    /** Alice's property with one electricity account numbered {@code accountNumber}. */
    private Setup setup(String accountNumber) throws Exception {
        String propertyId = create(alice, "/api/properties",
                "{\"name\": \"Review House %s\"}".formatted(UUID.randomUUID()));
        String hydroId = create(alice, "/api/properties/" + propertyId + "/utility-accounts",
                "{\"utilityType\": \"ELECTRICITY\", \"providerName\": \"Sample Hydro\", \"accountNumber\": \"%s\"}"
                        .formatted(accountNumber));
        return new Setup(propertyId, hydroId);
    }

    private String createAccount(RequestPostProcessor who, String propertyName, String accountNumber) throws Exception {
        String propertyId = create(who, "/api/properties", "{\"name\": \"%s\"}".formatted(propertyName));
        return create(who, "/api/properties/" + propertyId + "/utility-accounts",
                "{\"utilityType\": \"WATER\", \"providerName\": \"Water Co\", \"accountNumber\": \"%s\"}"
                        .formatted(accountNumber));
    }

    private void willRead(String json) {
        given(reader.read(any(byte[].class), anyString(), anyString()))
                .willReturn(new OpenAiBillReader.Reading(ExtractedBill.fromJson(json), json));
    }

    private String upload(RequestPostProcessor who, String accountId) throws Exception {
        return upload(who, accountId, TestFiles.pdf("review-" + UUID.randomUUID()));
    }

    private String upload(RequestPostProcessor who, String accountId, byte[] content) throws Exception {
        var request = multipart("/api/bills")
                .file(new MockMultipartFile("file", "hydro.pdf", "application/pdf", content)).with(who);
        if (accountId != null) {
            request.param("utilityAccountId", accountId);
        }
        String json = mockMvc.perform(request).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private ResultActions bill(RequestPostProcessor who, String billId) throws Exception {
        return mockMvc.perform(get("/api/bills/" + billId).with(who));
    }

    private ResultActions saveFields(RequestPostProcessor who, String billId, String accountId, String start,
            String end, String current) throws Exception {
        String body = """
                {"utilityAccountId": %s, "providerName": "Sample Hydro Inc.", "accountNumber": "1234 5678-9",
                 "periodStart": "%s", "periodEnd": "%s", "currentCharges": %s, "previousBalance": 0,
                 "amountDue": %s, "dueDate": "2026-10-20"}"""
                .formatted(accountId == null ? "null" : "\"" + accountId + "\"", start, end, current, current);
        return mockMvc.perform(put("/api/bills/" + billId + "/fields").with(who)
                .contentType(MediaType.APPLICATION_JSON).content(body));
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
