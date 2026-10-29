package com.propfolio.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import com.propfolio.documents.storage.StorageProperties;
import com.propfolio.documents.web.BillDtos.BillResponse;
import com.propfolio.security.CurrentLandlord;

/**
 * If the database transaction doesn't commit after the file was stored,
 * the stored file must be removed again (no orphan files).
 */
@SpringBootTest
@ActiveProfiles("test")
class BillUploadRollbackTest {

    private final UUID landlordId = UUID.randomUUID();

    @MockitoBean private CurrentLandlord currentLandlord;
    @Autowired private BillService billService;
    @Autowired private BillRepository bills;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private StorageProperties storageProperties;

    @BeforeEach
    void signIn() {
        given(currentLandlord.id()).willReturn(landlordId);
    }

    @Test
    void storedFileIsDeletedWhenTheTransactionRollsBack() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "rollback.pdf", "application/pdf",
                TestFiles.pdf("rollback-" + UUID.randomUUID()));

        BillResponse uploaded = transactionTemplate.execute(status -> {
            BillResponse response = billService.upload(file, null);
            assertThat(filesFor(landlordId)).hasSize(1); // written while the transaction is open
            status.setRollbackOnly();                    // e.g. the insert fails at commit
            return response;
        });

        assertThat(bills.findById(uploaded.id())).isEmpty();
        assertThat(filesFor(landlordId)).isEmpty();
    }

    @Test
    void storedFileIsKeptWhenTheTransactionCommits() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "kept.pdf", "application/pdf",
                TestFiles.pdf("commit-" + UUID.randomUUID()));

        BillResponse uploaded = billService.upload(file, null);

        assertThat(bills.findById(uploaded.id())).isPresent();
        assertThat(filesFor(landlordId)).hasSize(1);
    }

    private Stream<Path> filesFor(UUID landlord) throws Exception {
        Path folder = storageProperties.localRoot().toAbsolutePath().normalize().resolve(landlord.toString());
        if (!Files.exists(folder)) {
            return Stream.empty();
        }
        try (Stream<Path> walk = Files.walk(folder)) {
            return walk.filter(Files::isRegularFile).toList().stream();
        }
    }
}
