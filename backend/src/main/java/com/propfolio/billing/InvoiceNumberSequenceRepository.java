package com.propfolio.billing;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface InvoiceNumberSequenceRepository extends JpaRepository<InvoiceNumberSequence, UUID> {

    /** Locks the row until the transaction ends, so two requests never take the same number. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<InvoiceNumberSequence> findByLandlordIdAndSequenceYear(UUID landlordId, int sequenceYear);
}
