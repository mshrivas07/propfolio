package com.propfolio.documents.web;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.propfolio.documents.BillStatus;
import com.propfolio.rentals.UtilityType;

/**
 * Response bodies for /api/bills. Uploads are multipart, so there is no request record.
 */
public final class BillDtos {

    private BillDtos() {
    }

    /**
     * A bill and what is known about it. Extracted fields (period, amount, due date, confidence)
     * stay empty until Phase 4. The internal storage path is never exposed.
     */
    public record BillResponse(
            UUID id,
            BillStatus status,
            String originalFilename,
            String contentType,
            long fileSizeBytes,
            UUID utilityAccountId,
            UtilityType utilityType,
            String providerName,
            String accountNumber,
            UUID propertyId,
            String propertyName,
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal amountDue,
            LocalDate dueDate,
            BigDecimal confidence,
            Instant verifiedAt,
            Instant createdAt) {
    }

    /** An opened bill file, ready to stream back. The controller closes the stream. */
    public record BillFile(InputStream content, String contentType, long size, String filename) {
    }
}
