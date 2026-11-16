package com.propfolio.documents.web;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import com.propfolio.documents.BillStatus;
import com.propfolio.rentals.UtilityType;

/**
 * Request and response bodies for /api/bills. Uploads are multipart, so they have no request record.
 */
public final class BillDtos {

    private BillDtos() {
    }

    /** Where reading the bill (OpenAI extraction) stands. Derived, not stored. */
    public enum ExtractionState {
        /** Never requested (e.g. uploaded before Phase 4, or auto-reading is off). */
        NOT_STARTED,
        RUNNING,
        DONE,
        FAILED
    }

    /**
     * A bill and what is known about it. The internal storage path is never exposed.
     *
     * <p>{@code providerName} / {@code accountNumber} are for display: the linked utility account's
     * values when there is one, otherwise what was read from the bill. {@code billProviderName} /
     * {@code billAccountNumber} are the values on the bill itself (editable during review).
     *
     * <p>{@code fieldReview} and {@code issues} are filled for single-bill responses only;
     * list responses leave them empty.
     */
    public record BillResponse(
            UUID id,
            BillStatus status,
            ExtractionState extractionState,
            String extractionError,
            Instant extractedAt,
            String originalFilename,
            String contentType,
            long fileSizeBytes,
            UUID utilityAccountId,
            UtilityType utilityType,
            String providerName,
            String accountNumber,
            UUID propertyId,
            String propertyName,
            String billProviderName,
            String billAccountNumber,
            UtilityType billUtilityType,
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal currentCharges,
            BigDecimal previousBalance,
            BigDecimal amountDue,
            LocalDate dueDate,
            BigDecimal confidence,
            Map<String, FieldReview> fieldReview,
            List<ReviewIssue> issues,
            Instant verifiedAt,
            Instant createdAt) {
    }

    /**
     * How sure the reader was about one field, and whether the landlord has changed it.
     * {@code confidence} is null when the field wasn't read (e.g. typed in by hand).
     */
    public record FieldReview(BigDecimal confidence, boolean edited, boolean lowConfidence) {
    }

    /** Something to check before verifying. {@code field} is null for whole-bill problems. */
    public record ReviewIssue(String field, String message) {
    }

    /**
     * Review edits. PUT semantics: every field is replaced (null clears it).
     * Checks that need other data (dates in order, amounts adding up) are reported as review
     * issues, not rejected, so a half-finished review can still be saved.
     */
    public record BillFieldsRequest(
            UUID utilityAccountId,
            @Size(max = 120) String providerName,
            @Size(max = 60) String accountNumber,
            LocalDate periodStart,
            LocalDate periodEnd,
            @Digits(integer = 10, fraction = 2, message = "Use at most 2 decimal places") BigDecimal currentCharges,
            @Digits(integer = 10, fraction = 2, message = "Use at most 2 decimal places") BigDecimal previousBalance,
            @Digits(integer = 10, fraction = 2, message = "Use at most 2 decimal places") BigDecimal amountDue,
            LocalDate dueDate) {
    }

    /** An opened bill file, ready to stream back. The controller closes the stream. */
    public record BillFile(InputStream content, String contentType, long size, String filename) {
    }
}
