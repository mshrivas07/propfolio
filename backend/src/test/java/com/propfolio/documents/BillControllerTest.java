package com.propfolio.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.propfolio.billing.Invoice;
import com.propfolio.billing.InvoiceLine;
import com.propfolio.billing.InvoiceLineRepository;
import com.propfolio.billing.InvoiceRepository;
import com.propfolio.documents.storage.StorageProperties;
import com.propfolio.rentals.ShareType;

/**
 * Upload, list, view and delete through HTTP with two signed-in landlords.
 * Files are written to the test storage folder (see application-test.yml).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BillControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private BillRepository bills;
    @Autowired private InvoiceRepository invoices;
    @Autowired private InvoiceLineRepository invoiceLines;
    @Autowired private StorageProperties storageProperties;

    private final UUID aliceId = UUID.randomUUID();
    private final RequestPostProcessor alice = landlord(aliceId);
    private final RequestPostProcessor bob = landlord(UUID.randomUUID());

    @Test
    void uploadingAPdfStoresItAndReturns201() throws Exception {
        String id = uploadOk(alice, file("hydro-oct.pdf", "application/pdf", TestFiles.pdf("upload")), null);

        mockMvc.perform(get("/api/bills/" + id).with(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andExpect(jsonPath("$.originalFilename").value("hydro-oct.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.storagePath").doesNotExist());

        Bill bill = bills.findById(UUID.fromString(id)).orElseThrow();
        assertThat(bill.getStoragePath()).matches(aliceId + "/\\d{4}/\\d{2}/" + id + "\\.pdf");
        assertThat(bill.getFileSha256()).hasSize(64);
        assertThat(storedFile(bill)).exists();
    }

    @Test
    void contentTypeComesFromTheFileNotTheBrowser() throws Exception {
        String id = uploadOk(alice, file("photo.pdf", "application/pdf", TestFiles.png()), null);
        mockMvc.perform(get("/api/bills/" + id).with(alice)).andExpect(jsonPath("$.contentType").value("image/png"));
        assertThat(bills.findById(UUID.fromString(id)).orElseThrow().getStoragePath()).endsWith(".png");
    }

    @Test
    void jpegIsAccepted() throws Exception {
        String id = uploadOk(alice, file("scan.jpeg", "image/jpeg", TestFiles.jpeg()), null);
        mockMvc.perform(get("/api/bills/" + id).with(alice)).andExpect(jsonPath("$.contentType").value("image/jpeg"));
    }

    @Test
    void aTextFileCalledPdfIsRejected() throws Exception {
        upload(alice, file("bill.pdf", "application/pdf", "not really a pdf".getBytes()), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("file"))
                .andExpect(jsonPath("$.errors[0].message").value("Upload a PDF, PNG or JPG file"));
    }

    @Test
    void emptyFileIsRejected() throws Exception {
        upload(alice, file("empty.pdf", "application/pdf", new byte[0]), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("file"));
    }

    @Test
    void fileOver10MbIsRejected() throws Exception {
        upload(alice, file("big.pdf", "application/pdf", TestFiles.oversizedPdf()), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("Files can be at most 10 MB"));
    }

    @Test
    void missingFilePartIsA400() throws Exception {
        mockMvc.perform(multipart("/api/bills").with(alice)).andExpect(status().isBadRequest());
    }

    @Test
    void sameFileTwiceIsA409PointingAtTheFirstUpload() throws Exception {
        byte[] content = TestFiles.pdf("duplicate");
        String firstId = uploadOk(alice, file("first.pdf", "application/pdf", content), null);

        upload(alice, file("renamed.pdf", "application/pdf", content), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.existingBillId").value(firstId))
                .andExpect(jsonPath("$.detail").value("You already uploaded this file as \"first.pdf\"."));
    }

    @Test
    void twoLandlordsMayUploadTheSameFile() throws Exception {
        byte[] content = TestFiles.pdf("shared-" + UUID.randomUUID());
        uploadOk(alice, file("a.pdf", "application/pdf", content), null);
        uploadOk(bob, file("b.pdf", "application/pdf", content), null);
    }

    @Test
    void uploadCanBeLinkedToAUtilityAccount() throws Exception {
        String propertyId = createJson(alice, "/api/properties", "{\"name\": \"Bill House %s\"}".formatted(UUID.randomUUID()));
        String accountId = createJson(alice, "/api/properties/" + propertyId + "/utility-accounts",
                "{\"utilityType\": \"WATER\", \"providerName\": \"City Water\", \"accountNumber\": \"W-42\"}");

        String id = uploadOk(alice, file("water.pdf", "application/pdf", TestFiles.pdf("linked")), accountId);

        mockMvc.perform(get("/api/bills/" + id).with(alice))
                .andExpect(jsonPath("$.utilityAccountId").value(accountId))
                .andExpect(jsonPath("$.providerName").value("City Water"))
                .andExpect(jsonPath("$.accountNumber").value("W-42"))
                .andExpect(jsonPath("$.propertyId").value(propertyId))
                .andExpect(jsonPath("$.utilityType").value("WATER"));

        mockMvc.perform(get("/api/bills").param("propertyId", propertyId).with(alice))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id));
    }

    @Test
    void anotherLandlordsUtilityAccountIsAFieldError() throws Exception {
        String propertyId = createJson(bob, "/api/properties", "{\"name\": \"Bob House %s\"}".formatted(UUID.randomUUID()));
        String bobsAccount = createJson(bob, "/api/properties/" + propertyId + "/utility-accounts",
                "{\"utilityType\": \"GAS\", \"providerName\": \"Gas Co\"}");

        upload(alice, file("gas.pdf", "application/pdf", TestFiles.pdf("not-yours")), bobsAccount)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("utilityAccountId"));
    }

    @Test
    void listIsNewestFirstAndFiltersByStatus() throws Exception {
        String older = uploadOk(alice, file("older.pdf", "application/pdf", TestFiles.pdf("older")), null);
        Thread.sleep(20); // createdAt must differ for the ordering check
        String newer = uploadOk(alice, file("newer.pdf", "application/pdf", TestFiles.pdf("newer")), null);

        mockMvc.perform(get("/api/bills").with(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(newer))
                .andExpect(jsonPath("$[1].id").value(older));

        mockMvc.perform(get("/api/bills").param("status", "VERIFIED").with(alice))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/bills").param("status", "UPLOADED").with(alice))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void fileIsStreamedBackInlineWithItsType() throws Exception {
        byte[] content = TestFiles.pdf("stream");
        String id = uploadOk(alice, file("Hydro Bill.pdf", "application/pdf", content), null);

        mockMvc.perform(get("/api/bills/" + id + "/file").with(alice))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(content))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline")))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test
    void anotherLandlordsBillLooksLikeItDoesNotExist() throws Exception {
        String id = uploadOk(alice, file("private.pdf", "application/pdf", TestFiles.pdf("isolation")), null);

        mockMvc.perform(get("/api/bills/" + id).with(bob)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/bills/" + id + "/file").with(bob)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/bills/" + id).with(bob)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/bills").with(bob))
                .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(id)).isEmpty());

        assertThat(bills.findById(UUID.fromString(id))).isPresent();
    }

    @Test
    void deletingRemovesTheRowAndTheFile() throws Exception {
        String id = uploadOk(alice, file("delete-me.pdf", "application/pdf", TestFiles.pdf("delete")), null);
        Path stored = storedFile(bills.findById(UUID.fromString(id)).orElseThrow());

        mockMvc.perform(delete("/api/bills/" + id).with(alice)).andExpect(status().isNoContent());

        assertThat(bills.findById(UUID.fromString(id))).isEmpty();
        assertThat(stored).doesNotExist();
    }

    @Test
    void verifiedBillCannotBeDeleted() throws Exception {
        String id = uploadOk(alice, file("verified.pdf", "application/pdf", TestFiles.pdf("verified")), null);
        Bill bill = bills.findById(UUID.fromString(id)).orElseThrow();
        bill.setStatus(BillStatus.VERIFIED);
        bills.save(bill);

        mockMvc.perform(delete("/api/bills/" + id).with(alice))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Verified bills can't be deleted."));
        assertThat(storedFile(bill)).exists();
    }

    @Test
    void billOnAnInvoiceCannotBeDeleted() throws Exception {
        String id = uploadOk(alice, file("invoiced.pdf", "application/pdf", TestFiles.pdf("invoiced")), null);
        UUID unitId = UUID.randomUUID();
        Invoice invoice = invoices.save(new Invoice(aliceId, "INV-T-" + UUID.randomUUID(), UUID.randomUUID(), unitId,
                LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30")));
        invoiceLines.save(new InvoiceLine(aliceId, invoice.getId(), UUID.fromString(id), unitId,
                LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30"), 30, 30, ShareType.PERCENT,
                new java.math.BigDecimal("70"), new java.math.BigDecimal("10.00")));

        mockMvc.perform(delete("/api/bills/" + id).with(alice))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This bill is on an invoice, so it can't be deleted."));
    }

    @Test
    void requestsWithoutATokenReturn401() throws Exception {
        mockMvc.perform(get("/api/bills")).andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/bills").file(file("x.pdf", "application/pdf", TestFiles.pdf("anon"))))
                .andExpect(status().isUnauthorized());
    }

    private Path storedFile(Bill bill) {
        return storageProperties.localRoot().toAbsolutePath().normalize().resolve(bill.getStoragePath());
    }

    private String uploadOk(RequestPostProcessor who, MockMultipartFile file, String accountId) throws Exception {
        String json = upload(who, file, accountId).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private ResultActions upload(RequestPostProcessor who, MockMultipartFile file, String accountId)
            throws Exception {
        var request = multipart("/api/bills").file(file).with(who);
        if (accountId != null) {
            request.param("utilityAccountId", accountId);
        }
        return mockMvc.perform(request);
    }

    private String createJson(RequestPostProcessor who, String path, String body) throws Exception {
        String json = mockMvc.perform(post(path).with(who).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private static MockMultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("file", name, contentType, content);
    }

    private static RequestPostProcessor landlord(UUID id) {
        return jwt().jwt(token -> token.subject(id.toString()).claim("email", id + "@example.com"));
    }
}
