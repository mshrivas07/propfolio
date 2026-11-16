package com.propfolio.documents;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.propfolio.documents.web.BillDtos.BillResponse;
import com.propfolio.documents.web.BillDtos.ExtractionState;
import com.propfolio.documents.web.BillDtos.SplitBasis;
import com.propfolio.rentals.Property;
import com.propfolio.rentals.PropertyRepository;
import com.propfolio.rentals.UtilityAccount;
import com.propfolio.rentals.UtilityAccountRepository;

/**
 * Builds bill responses, loading linked accounts and properties once per batch.
 * Single-bill responses also include the review (field confidence and issues).
 */
@Component
public class BillResponseMapper {

    private static final Logger log = LoggerFactory.getLogger(BillResponseMapper.class);

    private final UtilityAccountRepository utilityAccounts;
    private final PropertyRepository properties;
    private final BillRepository bills;
    private final BillProcessingProperties settings;
    private final Clock clock;

    public BillResponseMapper(UtilityAccountRepository utilityAccounts, PropertyRepository properties,
            BillRepository bills, BillProcessingProperties settings, Clock clock) {
        this.utilityAccounts = utilityAccounts;
        this.properties = properties;
        this.bills = bills;
        this.settings = settings;
        this.clock = clock;
    }

    public List<BillResponse> toList(List<Bill> rows) {
        return build(rows, false);
    }

    public BillResponse toDetail(Bill bill) {
        return build(List.of(bill), true).getFirst();
    }

    /** Runs the review rules for one bill (used for responses and for setting READY / NEEDS_REVIEW). */
    public BillChecks.Result review(Bill bill) {
        UtilityAccount account = bill.getUtilityAccountId() == null ? null
                : utilityAccounts.findById(bill.getUtilityAccountId()).orElse(null);
        List<Bill> overlapping = account == null || bill.getPeriodStart() == null || bill.getPeriodEnd() == null
                || bill.getPeriodEnd().isBefore(bill.getPeriodStart())
                ? List.of()
                : bills.findOverlappingPeriod(account.getId(), bill.getPeriodStart(), bill.getPeriodEnd(), bill.getId());
        return BillChecks.evaluate(bill, extracted(bill), account, overlapping, settings.readyThreshold());
    }

    /** What the reader stored for this bill, or null if never read (or unreadable). */
    public static ExtractedBill extracted(Bill bill) {
        if (bill.getExtractionJson() == null) {
            return null;
        }
        try {
            return ExtractedBill.fromJson(bill.getExtractionJson());
        } catch (IllegalArgumentException e) {
            log.warn("Stored extraction for bill {} can't be parsed", bill.getId());
            return null;
        }
    }

    public ExtractionState extractionState(Bill bill) {
        Instant requested = bill.getExtractionRequestedAt();
        if (requested == null) {
            return ExtractionState.NOT_STARTED;
        }
        if (bill.getExtractionError() != null) {
            return ExtractionState.FAILED;
        }
        if (bill.getExtractedAt() != null && !bill.getExtractedAt().isBefore(requested)) {
            return ExtractionState.DONE;
        }
        if (requested.plus(settings.extractionStalledAfter()).isBefore(clock.instant())) {
            return ExtractionState.FAILED;
        }
        return ExtractionState.RUNNING;
    }

    private String extractionError(Bill bill, ExtractionState state) {
        if (state != ExtractionState.FAILED) {
            return null;
        }
        return bill.getExtractionError() != null ? bill.getExtractionError()
                : "Reading this bill didn't finish. Try again.";
    }

    private List<BillResponse> build(List<Bill> rows, boolean detail) {
        List<UUID> accountIds = rows.stream().map(Bill::getUtilityAccountId).filter(id -> id != null).distinct()
                .toList();
        Map<UUID, UtilityAccount> accountById = accountIds.isEmpty() ? Map.of()
                : byId(utilityAccounts.findAllById(accountIds), UtilityAccount::getId);
        Map<UUID, Property> propertyById = accountById.isEmpty() ? Map.of()
                : byId(properties.findAllById(accountById.values().stream().map(UtilityAccount::getPropertyId)
                        .distinct().toList()), Property::getId);

        return rows.stream().map(bill -> {
            // Map.of() rejects get(null), so check for "no account linked" first.
            UtilityAccount account = bill.getUtilityAccountId() == null ? null
                    : accountById.get(bill.getUtilityAccountId());
            Property property = account == null ? null : propertyById.get(account.getPropertyId());
            ExtractionState state = extractionState(bill);
            BillChecks.Result review = detail ? review(bill) : null;
            ExtractedBill extracted = detail ? extracted(bill) : null;
            boolean plan = account != null && account.isEqualPaymentPlan();
            SplitBasis basis = plan ? SplitBasis.PLAN_PAYMENT : SplitBasis.CURRENT_CHARGES;
            BigDecimal amountToSplit = bill.getStatus() == BillStatus.VERIFIED && bill.getSplitAmount() != null
                    ? bill.getSplitAmount()
                    : plan ? bill.getAmountDue() : bill.getCurrentCharges();
            return new BillResponse(bill.getId(), bill.getStatus(), state, extractionError(bill, state),
                    bill.getExtractedAt(), bill.getOriginalFilename(), bill.getContentType(), bill.getFileSizeBytes(),
                    bill.getUtilityAccountId(),
                    account == null ? null : account.getUtilityType(),
                    account != null ? account.getProviderName() : bill.getProviderName(),
                    account != null && account.getAccountNumber() != null ? account.getAccountNumber()
                            : bill.getAccountNumber(),
                    property == null ? null : property.getId(), property == null ? null : property.getName(),
                    bill.getProviderName(), bill.getAccountNumber(),
                    extracted == null ? null : extracted.utilityType(),
                    bill.getPeriodStart(), bill.getPeriodEnd(), bill.getCurrentCharges(), bill.getPreviousBalance(),
                    bill.getAmountDue(), bill.getDueDate(), plan, basis, amountToSplit, bill.getSplitAmount(),
                    bill.getConfidence(),
                    review == null ? Map.of() : review.fieldReview(),
                    review == null ? List.of() : review.issues(),
                    bill.getVerifiedAt(), bill.getCreatedAt());
        }).toList();
    }

    private static <T> Map<UUID, T> byId(List<T> rows, Function<T, UUID> id) {
        return rows.stream().collect(Collectors.toMap(id, Function.identity()));
    }
}
