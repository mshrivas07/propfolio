package com.propfolio.extraction;

import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.propfolio.documents.BillExtractionRequested;

/**
 * Starts a reading once the transaction that asked for it has committed
 * (so the bill row and file are really there), on the extraction thread pool.
 */
@Component
public class ExtractionTrigger {

    private static final Logger log = LoggerFactory.getLogger(ExtractionTrigger.class);

    private final BillExtractionJob job;
    private final ExtractionExecutor executor;

    public ExtractionTrigger(BillExtractionJob job, ExtractionExecutor executor) {
        this.job = job;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onExtractionRequested(BillExtractionRequested event) {
        try {
            executor.execute(() -> job.run(event.billId(), event.requestedAt()));
        } catch (RejectedExecutionException e) {
            // The bill shows as failed once the stall time passes and can be read again.
            log.warn("Too many bills waiting to be read; bill {} wasn't queued", event.billId());
        }
    }
}
