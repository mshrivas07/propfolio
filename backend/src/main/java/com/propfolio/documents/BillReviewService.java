package com.propfolio.documents;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.propfolio.billing.InvoiceLineRepository;
import com.propfolio.common.error.ConflictException;
import com.propfolio.common.error.FieldError;
import com.propfolio.common.error.InvalidRequestException;
import com.propfolio.common.error.NotFoundException;
import com.propfolio.documents.web.BillDtos.BillFieldsRequest;
import com.propfolio.documents.web.BillDtos.BillResponse;
import com.propfolio.rentals.UtilityAccount;
import com.propfolio.rentals.UtilityAccountRepository;
import com.propfolio.security.CurrentLandlord;

/**
 * The bill's life after upload: reading results, review edits, verify / un-verify.
 *
 * <pre>
 * UPLOADED ──read──▶ READY or NEEDS_REVIEW ──verify──▶ VERIFIED
 *    ▲   (reading failed: stays UPLOADED            │
 *    │    with extraction_error)          un-verify │ (only while not on a live invoice)
 *    └── re-run extraction ◀── READY / NEEDS_REVIEW ◀┘
 * </pre>
 *
 * Status is re-evaluated (READY vs NEEDS_REVIEW) after every reading and every edit.
 * EXTRACTED is not used as a resting state.
 */
@Service
@Transactional
public class BillReviewService {

    private final CurrentLandlord currentLandlord;
    private final BillRepository bills;
    private final UtilityAccountRepository utilityAccounts;
    private final InvoiceLineRepository invoiceLines;
    private final BillResponseMapper mapper;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public BillReviewService(CurrentLandlord currentLandlord, BillRepository bills,
            UtilityAccountRepository utilityAccounts, InvoiceLineRepository invoiceLines, BillResponseMapper mapper,
            ApplicationEventPublisher events, Clock clock) {
        this.currentLandlord = currentLandlord;
        this.bills = bills;
        this.utilityAccounts = utilityAccounts;
        this.invoiceLines = invoiceLines;
        this.mapper = mapper;
        this.events = events;
        this.clock = clock;
    }

    // ---- Called from the API (signed-in landlord) -------------------------------------------

    /** "Re-run extraction": reads the bill again in the background. Overwrites the read fields. */
    public BillResponse requestExtraction(UUID billId) {
        Bill bill = require(billId);
        if (bill.getStatus() == BillStatus.VERIFIED) {
            throw new ConflictException("This bill is verified. Un-verify it before reading it again.");
        }
        startExtraction(bill, false);
        return mapper.toDetail(bill);
    }

    public BillResponse updateFields(UUID billId, BillFieldsRequest request) {
        UUID landlordId = currentLandlord.id();
        Bill bill = require(billId);
        if (bill.getStatus() == BillStatus.VERIFIED) {
            throw new ConflictException("This bill is verified. Un-verify it to make changes.");
        }
        if (request.utilityAccountId() != null) {
            utilityAccounts.findByIdAndLandlordId(request.utilityAccountId(), landlordId)
                    .orElseThrow(() -> new InvalidRequestException("utilityAccountId",
                            "Choose a utility account from your properties"));
        }
        bill.setUtilityAccountId(request.utilityAccountId());
        bill.setProviderName(clean(request.providerName()));
        bill.setAccountNumber(clean(request.accountNumber()));
        bill.setPeriodStart(request.periodStart());
        bill.setPeriodEnd(request.periodEnd());
        bill.setCurrentCharges(request.currentCharges());
        bill.setPreviousBalance(request.previousBalance());
        bill.setAmountDue(request.amountDue());
        bill.setDueDate(request.dueDate());
        reevaluate(bill);
        return mapper.toDetail(bill);
    }

    public BillResponse verify(UUID billId) {
        Bill bill = require(billId);
        if (bill.getStatus() == BillStatus.VERIFIED) {
            return mapper.toDetail(bill);
        }
        List<FieldError> missing = new ArrayList<>();
        if (bill.getUtilityAccountId() == null) {
            missing.add(new FieldError("utilityAccountId", "Choose the utility account this bill belongs to"));
        }
        if (bill.getPeriodStart() == null) {
            missing.add(new FieldError(ExtractedBill.PERIOD_START, "Enter the first day of the billing period"));
        }
        if (bill.getPeriodEnd() == null) {
            missing.add(new FieldError(ExtractedBill.PERIOD_END, "Enter the last day of the billing period"));
        } else if (bill.getPeriodStart() != null && bill.getPeriodEnd().isBefore(bill.getPeriodStart())) {
            missing.add(new FieldError(ExtractedBill.PERIOD_END, "The billing period ends before it starts"));
        }
        if (bill.getCurrentCharges() == null) {
            missing.add(new FieldError(ExtractedBill.CURRENT_CHARGES, "Enter this period's charges"));
        } else if (bill.getCurrentCharges().signum() <= 0) {
            missing.add(new FieldError(ExtractedBill.CURRENT_CHARGES, "Current charges must be more than $0"));
        }
        if (!missing.isEmpty()) {
            throw new InvalidRequestException(missing);
        }

        // Two verified bills for the same days would charge tenants twice.
        bills.findOverlappingPeriod(bill.getUtilityAccountId(), bill.getPeriodStart(), bill.getPeriodEnd(),
                bill.getId()).stream()
                .filter(other -> other.getStatus() == BillStatus.VERIFIED)
                .findFirst()
                .ifPresent(other -> {
                    throw new ConflictException(("Another verified bill for this account (%s, %s to %s) already covers "
                            + "some of these dates. Tenants would be charged twice.").formatted(
                                    other.getOriginalFilename(), other.getPeriodStart(), other.getPeriodEnd()));
                });

        bill.setStatus(BillStatus.VERIFIED);
        bill.setVerifiedAt(now());
        return mapper.toDetail(bill);
    }

    public BillResponse unverify(UUID billId) {
        Bill bill = require(billId);
        if (bill.getStatus() != BillStatus.VERIFIED) {
            return mapper.toDetail(bill);
        }
        if (invoiceLines.existsByBillIdAndVoidedFalse(bill.getId())) {
            throw new ConflictException("This bill is on an invoice. Void that invoice before un-verifying the bill.");
        }
        bill.setVerifiedAt(null);
        bill.setStatus(BillStatus.NEEDS_REVIEW); // replaced by reevaluate
        reevaluate(bill);
        return mapper.toDetail(bill);
    }

    // ---- Called by documents (upload) and the extraction job --------------------------------

    /** Marks the bill as waiting to be read and asks for a reading once the transaction commits. */
    public void startExtraction(Bill bill, boolean automatic) {
        Instant requestedAt = now();
        bill.setExtractionRequestedAt(requestedAt);
        bill.setExtractionError(null);
        bill.setStatus(BillStatus.UPLOADED);
        events.publishEvent(new BillExtractionRequested(bill.getId(), requestedAt, automatic));
    }

    /**
     * Stores a successful reading. Ignored if the bill was deleted, verified, or asked to be read
     * again since this reading started.
     */
    public void applyExtraction(UUID billId, Instant requestedAt, ExtractedBill extracted, String rawJson) {
        Bill bill = bills.findById(billId).orElse(null);
        if (!isCurrentRequest(bill, requestedAt)) {
            return;
        }
        bill.setProviderName(extracted.providerName());
        bill.setAccountNumber(extracted.accountNumber());
        bill.setPeriodStart(extracted.periodStart());
        bill.setPeriodEnd(extracted.periodEnd());
        bill.setCurrentCharges(extracted.currentCharges());
        bill.setPreviousBalance(extracted.previousBalance());
        bill.setAmountDue(extracted.amountDue());
        bill.setDueDate(extracted.dueDate());
        bill.setExtractionJson(rawJson);
        bill.setExtractionError(null);
        bill.setExtractedAt(now());

        if (bill.getUtilityAccountId() == null && extracted.accountNumber() != null) {
            List<UtilityAccount> matches = utilityAccounts.findAllByLandlordId(bill.getLandlordId()).stream()
                    .filter(account -> AccountNumbers.same(account.getAccountNumber(), extracted.accountNumber()))
                    .toList();
            if (matches.size() == 1) {
                bill.setUtilityAccountId(matches.getFirst().getId());
            }
        }
        reevaluate(bill);
    }

    /** Stores a failed reading; the bill stays UPLOADED so it can be read again or filled in by hand. */
    public void markExtractionFailed(UUID billId, Instant requestedAt, String message) {
        Bill bill = bills.findById(billId).orElse(null);
        if (!isCurrentRequest(bill, requestedAt)) {
            return;
        }
        bill.setExtractionError(message);
    }

    /** True if this reading is still wanted for the bill. */
    public boolean isCurrentRequest(Bill bill, Instant requestedAt) {
        return bill != null && bill.getStatus() != BillStatus.VERIFIED
                && requestedAt.equals(bill.getExtractionRequestedAt());
    }

    private void reevaluate(Bill bill) {
        BillChecks.Result review = mapper.review(bill);
        bill.setConfidence(review.confidence());
        if (bill.getStatus() != BillStatus.VERIFIED) {
            bill.setStatus(review.ready() ? BillStatus.READY : BillStatus.NEEDS_REVIEW);
        }
    }

    private Bill require(UUID billId) {
        return bills.findByIdAndLandlordId(billId, currentLandlord.id())
                .orElseThrow(() -> new NotFoundException("Bill"));
    }

    /** Millisecond precision so the value survives a round trip through the database unchanged. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
