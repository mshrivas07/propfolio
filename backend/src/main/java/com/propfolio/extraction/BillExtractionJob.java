package com.propfolio.extraction;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.propfolio.common.error.NotFoundException;
import com.propfolio.documents.Bill;
import com.propfolio.documents.BillRepository;
import com.propfolio.documents.BillReviewService;
import com.propfolio.documents.storage.StorageService;

/**
 * Reads one bill: load the file, ask OpenAI, store the result (or the error).
 *
 * <p>The OpenAI call happens outside any database transaction, so no connection is held for the
 * tens of seconds a reading can take. Each database step runs in its own new transaction
 * (it may be called right after another transaction commits, on the same thread).
 */
@Component
public class BillExtractionJob {

    private static final Logger log = LoggerFactory.getLogger(BillExtractionJob.class);

    private record FileToRead(String storagePath, String contentType, String filename) {
    }

    private final BillRepository bills;
    private final BillReviewService reviewService;
    private final StorageService storage;
    private final OpenAiBillReader reader;
    private final TransactionTemplate newTransaction;

    public BillExtractionJob(BillRepository bills, BillReviewService reviewService, StorageService storage,
            OpenAiBillReader reader, PlatformTransactionManager transactionManager) {
        this.bills = bills;
        this.reviewService = reviewService;
        this.storage = storage;
        this.reader = reader;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void run(UUID billId, Instant requestedAt) {
        FileToRead file = newTransaction.execute(status -> {
            Bill bill = bills.findById(billId).orElse(null);
            return reviewService.isCurrentRequest(bill, requestedAt)
                    ? new FileToRead(bill.getStoragePath(), bill.getContentType(), bill.getOriginalFilename())
                    : null;
        });
        if (file == null) {
            return; // deleted, verified or re-requested meanwhile
        }

        try {
            byte[] content;
            try (InputStream in = storage.open(file.storagePath())) {
                content = in.readAllBytes();
            }
            OpenAiBillReader.Reading reading = reader.read(content, file.contentType(), file.filename());
            newTransaction.executeWithoutResult(status ->
                    reviewService.applyExtraction(billId, requestedAt, reading.bill(), reading.rawJson()));
            log.info("Read bill {}", billId);
        } catch (ExtractionException e) {
            log.warn("Couldn't read bill {}: {}", billId, e.getMessage());
            fail(billId, requestedAt, e.getMessage());
        } catch (NotFoundException e) {
            log.warn("Couldn't read bill {}: stored file is missing", billId);
            fail(billId, requestedAt, "The bill's file is missing from storage. Delete this bill and upload it again.");
        } catch (IOException | RuntimeException e) {
            // Class name only: some exception messages can echo bill content.
            log.error("Unexpected error reading bill {} ({})", billId, e.getClass().getName());
            log.debug("Unexpected error details", e);
            fail(billId, requestedAt, "Something went wrong reading this bill. Try again, or fill in the details by hand.");
        }
    }

    private void fail(UUID billId, Instant requestedAt, String message) {
        newTransaction.executeWithoutResult(status -> reviewService.markExtractionFailed(billId, requestedAt, message));
    }
}
