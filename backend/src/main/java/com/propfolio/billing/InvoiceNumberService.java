package com.propfolio.billing;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Hands out invoice numbers: INV-YYYY-NNNN, one running sequence per landlord per year,
 * shared by all properties. The sequence row is locked while a number is taken, so two
 * requests at the same moment never get the same number.
 */
@Service
public class InvoiceNumberService {

    private final InvoiceNumberSequenceRepository sequences;
    private final TransactionTemplate newTransaction;

    public InvoiceNumberService(InvoiceNumberSequenceRepository sequences, PlatformTransactionManager transactionManager) {
        this.sequences = sequences;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public String next(UUID landlordId, int year) {
        InvoiceNumberSequence sequence = sequences.findByLandlordIdAndSequenceYear(landlordId, year)
                .orElseGet(() -> {
                    createSequence(landlordId, year);
                    return sequences.findByLandlordIdAndSequenceYear(landlordId, year).orElseThrow();
                });
        int value = sequence.getNextValue();
        sequence.setNextValue(value + 1);
        return format(year, value);
    }

    static String format(int year, int value) {
        return "INV-%d-%04d".formatted(year, value);
    }

    /** First invoice of the year: create the row in its own transaction (another request may win the race). */
    private void createSequence(UUID landlordId, int year) {
        try {
            newTransaction.executeWithoutResult(status ->
                    sequences.saveAndFlush(new InvoiceNumberSequence(landlordId, year)));
        } catch (DataIntegrityViolationException createdByAnotherRequest) {
            // The row exists now; the caller locks and reads it.
        }
    }
}
