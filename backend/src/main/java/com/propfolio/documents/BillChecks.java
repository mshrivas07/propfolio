package com.propfolio.documents;

import static com.propfolio.documents.ExtractedBill.ACCOUNT_NUMBER;
import static com.propfolio.documents.ExtractedBill.AMOUNT_DUE;
import static com.propfolio.documents.ExtractedBill.CURRENT_CHARGES;
import static com.propfolio.documents.ExtractedBill.DUE_DATE;
import static com.propfolio.documents.ExtractedBill.PERIOD_END;
import static com.propfolio.documents.ExtractedBill.PERIOD_START;
import static com.propfolio.documents.ExtractedBill.PREVIOUS_BALANCE;
import static com.propfolio.documents.ExtractedBill.PROVIDER_NAME;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.propfolio.documents.web.BillDtos.FieldReview;
import com.propfolio.documents.web.BillDtos.ReviewIssue;
import com.propfolio.rentals.UtilityAccount;
import com.propfolio.rentals.UtilityType;

/**
 * The review rules for a bill. Pure logic: no database, no clock.
 *
 * <p>Combined confidence = average reader confidence over the key fields (provider, account number,
 * period start/end, current charges), where a field the landlord typed or changed counts as 1.0
 * and a missing field as 0; then reduced by 15% for each failed rule. On an equal payment plan account
 * the plan payment (amount due) is what gets split, so it takes the place of current charges, and the
 * "amount due = current + previous" check is skipped. A bill is READY only when
 * there are no issues at all and the combined confidence reaches the threshold.
 */
public final class BillChecks {

    static final int MAX_PERIOD_DAYS = 120;
    static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.05");
    private static final BigDecimal RULE_PENALTY = new BigDecimal("0.15");
    private static final List<String> KEY_FIELDS = List.of(PROVIDER_NAME, ACCOUNT_NUMBER, PERIOD_START, PERIOD_END,
            CURRENT_CHARGES);
    /** On an equal payment plan the plan payment (amount due) is split, so it replaces current charges. */
    private static final List<String> PLAN_KEY_FIELDS = List.of(PROVIDER_NAME, ACCOUNT_NUMBER, PERIOD_START, PERIOD_END,
            AMOUNT_DUE);
    private static final List<String> REVIEWED_FIELDS = List.of(PROVIDER_NAME, ACCOUNT_NUMBER, PERIOD_START,
            PERIOD_END, CURRENT_CHARGES, PREVIOUS_BALANCE, AMOUNT_DUE, DUE_DATE);
    /** Words too common in provider names to show two providers are the same company. */
    private static final Set<String> GENERIC_WORDS = Set.of("the", "inc", "ltd", "corp", "corporation", "company",
            "utility", "utilities", "services", "service", "energy", "power", "city", "town", "of", "and");

    private BillChecks() {
    }

    /** The outcome of reviewing a bill. */
    public record Result(List<ReviewIssue> issues, BigDecimal confidence, boolean ready,
            Map<String, FieldReview> fieldReview) {
    }

    /**
     * @param bill        the bill with its current (possibly edited) values
     * @param extracted   what the reader found, or null if the bill was never read
     * @param account     the linked utility account, or null
     * @param overlapping other bills on the same account whose period overlaps this one
     * @param threshold   READY threshold (0–1)
     */
    public static Result evaluate(Bill bill, ExtractedBill extracted, UtilityAccount account,
            List<Bill> overlapping, BigDecimal threshold) {
        List<ReviewIssue> issues = new ArrayList<>();
        int ruleFailures = 0;
        boolean plan = account != null && account.isEqualPaymentPlan();

        if (extracted != null && !extracted.utilityBill()) {
            issues.add(new ReviewIssue(null, "This file doesn't look like a utility bill."));
            ruleFailures++;
        }

        // Billing period
        LocalDate start = bill.getPeriodStart();
        LocalDate end = bill.getPeriodEnd();
        if (start == null) {
            issues.add(new ReviewIssue(PERIOD_START, "Enter the first day of the billing period."));
            ruleFailures++;
        }
        if (end == null) {
            issues.add(new ReviewIssue(PERIOD_END, "Enter the last day of the billing period."));
            ruleFailures++;
        }
        if (start != null && end != null) {
            if (end.isBefore(start)) {
                issues.add(new ReviewIssue(PERIOD_END, "The billing period ends before it starts."));
                ruleFailures++;
            } else {
                long days = ChronoUnit.DAYS.between(start, end) + 1;
                if (days > MAX_PERIOD_DAYS) {
                    issues.add(new ReviewIssue(PERIOD_END, ("The billing period is %d days long. Bills usually cover "
                            + "up to about 3 months, so check the dates.").formatted(days)));
                    ruleFailures++;
                }
            }
        }

        // Amounts
        BigDecimal current = bill.getCurrentCharges();
        BigDecimal previous = bill.getPreviousBalance();
        BigDecimal due = bill.getAmountDue();
        if (plan) {
            if (due == null) {
                issues.add(new ReviewIssue(AMOUNT_DUE, "Enter the plan payment (amount due). It's what gets split."));
                ruleFailures++;
            } else if (due.signum() <= 0) {
                issues.add(new ReviewIssue(AMOUNT_DUE, ("The plan payment is %s. Only amounts above $0 can be split "
                        + "with tenants.").formatted(money(due))));
                ruleFailures++;
            }
        } else if (current == null) {
            issues.add(new ReviewIssue(CURRENT_CHARGES, "Enter this period's charges."));
            ruleFailures++;
        } else if (current.signum() <= 0) {
            issues.add(new ReviewIssue(CURRENT_CHARGES, ("Current charges are %s. Only charges above $0 can be split "
                    + "with tenants.").formatted(money(current))));
            ruleFailures++;
        }
        if (!plan && current != null && previous != null && due != null
                && current.add(previous).subtract(due).abs().compareTo(AMOUNT_TOLERANCE) > 0) {
            issues.add(new ReviewIssue(AMOUNT_DUE, ("Amount due (%s) isn't current charges (%s) plus previous balance "
                    + "(%s). Check that current charges don't include an older balance.")
                    .formatted(money(due), money(current), money(previous))));
            ruleFailures++;
        }

        // Utility account
        String billNumber = bill.getAccountNumber();
        if (account == null) {
            issues.add(new ReviewIssue("utilityAccountId", billNumber == null
                    ? "No account number was found. Choose the utility account this bill belongs to."
                    : ("None of your utility accounts has account number %s. Choose the right account, or add this "
                            + "number to it on the property page.").formatted(billNumber)));
            ruleFailures++;
        } else {
            if (billNumber != null && account.getAccountNumber() != null
                    && !AccountNumbers.same(billNumber, account.getAccountNumber())) {
                issues.add(new ReviewIssue(ACCOUNT_NUMBER, ("The bill's account number (%s) doesn't match the chosen "
                        + "account (%s).").formatted(billNumber, account.getAccountNumber())));
                ruleFailures++;
            }
            UtilityType billType = extracted == null ? null : extracted.utilityType();
            if (billType != null && billType != UtilityType.OTHER && account.getUtilityType() != UtilityType.OTHER
                    && billType != account.getUtilityType()) {
                issues.add(new ReviewIssue("utilityAccountId", "This looks like %s bill, but it's linked to %s account."
                        .formatted(article(billType), article(account.getUtilityType()))));
                ruleFailures++;
            }
            Boolean billOnPlan = extracted == null ? null : extracted.onPaymentPlan();
            if (Boolean.TRUE.equals(billOnPlan) && !plan) {
                issues.add(new ReviewIssue("utilityAccountId", "This bill looks like it's on an equal payment plan, but "
                        + "the account isn't marked as one. Tick \"Equal payment plan\" on the account if tenants "
                        + "should pay a share of the plan payment."));
                ruleFailures++;
            } else if (Boolean.FALSE.equals(billOnPlan) && plan) {
                issues.add(new ReviewIssue("utilityAccountId", "The account is marked as an equal payment plan, but "
                        + "this bill doesn't look like a plan bill."));
                ruleFailures++;
            }
            if (bill.getProviderName() != null && !sameProvider(bill.getProviderName(), account.getProviderName())) {
                issues.add(new ReviewIssue(PROVIDER_NAME, "The provider on the bill (%s) doesn't look like the "
                        .formatted(bill.getProviderName()) + "account's provider (%s).".formatted(account.getProviderName())));
                ruleFailures++;
            }
        }
        if (bill.getProviderName() == null) {
            issues.add(new ReviewIssue(PROVIDER_NAME, "No provider name was found."));
            ruleFailures++;
        }

        for (Bill other : overlapping) {
            issues.add(new ReviewIssue(PERIOD_START, ("Another bill for this account (%s, %s to %s) already covers some "
                    + "of these dates.").formatted(other.getOriginalFilename(), other.getPeriodStart(),
                            other.getPeriodEnd())));
            ruleFailures++;
        }

        // Per-field confidence
        Map<String, FieldReview> fieldReview = new LinkedHashMap<>();
        for (String field : REVIEWED_FIELDS) {
            BigDecimal score = extracted == null ? null : extracted.confidenceFor(field);
            boolean edited = extracted == null || !Objects.equals(currentValue(bill, field), extractedValue(extracted, field));
            boolean present = currentValue(bill, field) != null;
            boolean low = !edited && present && score != null && score.compareTo(threshold) < 0;
            fieldReview.put(field, new FieldReview(score, edited, low));
            if (low) {
                issues.add(new ReviewIssue(field, "The reader was only %d%% sure about this. Check it against the bill."
                        .formatted(score.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValue())));
            }
        }

        List<String> keyFields = plan ? PLAN_KEY_FIELDS : KEY_FIELDS;
        BigDecimal sum = BigDecimal.ZERO;
        for (String field : keyFields) {
            if (currentValue(bill, field) == null) {
                continue; // counts as 0
            }
            FieldReview review = fieldReview.get(field);
            sum = sum.add(review.edited() || review.confidence() == null ? BigDecimal.ONE : review.confidence());
        }
        BigDecimal average = sum.divide(BigDecimal.valueOf(keyFields.size()), 3, RoundingMode.HALF_UP);
        BigDecimal penalty = BigDecimal.ONE.subtract(RULE_PENALTY.multiply(BigDecimal.valueOf(ruleFailures)))
                .max(BigDecimal.ZERO);
        BigDecimal confidence = average.multiply(penalty).setScale(3, RoundingMode.HALF_UP);

        boolean ready = issues.isEmpty() && confidence.compareTo(threshold) >= 0;
        return new Result(List.copyOf(issues), confidence, ready, fieldReview);
    }

    /** Two provider names match if they share a distinctive word ("Sample Hydro Inc." ~ "Sample Hydro"). */
    static boolean sameProvider(String a, String b) {
        if (a == null || b == null) {
            return true;
        }
        Set<String> left = words(a);
        Set<String> right = words(b);
        if (left.isEmpty() || right.isEmpty()) {
            return true; // nothing distinctive to compare
        }
        return left.stream().anyMatch(right::contains);
    }

    private static Set<String> words(String name) {
        return Arrays.stream(name.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(word -> word.length() >= 3 && !GENERIC_WORDS.contains(word))
                .collect(Collectors.toSet());
    }

    private static Object currentValue(Bill bill, String field) {
        return FIELD_GETTERS.get(field).apply(bill);
    }

    private static Object extractedValue(ExtractedBill extracted, String field) {
        return switch (field) {
            case PROVIDER_NAME -> extracted.providerName();
            case ACCOUNT_NUMBER -> extracted.accountNumber();
            case PERIOD_START -> extracted.periodStart();
            case PERIOD_END -> extracted.periodEnd();
            case CURRENT_CHARGES -> extracted.currentCharges();
            case PREVIOUS_BALANCE -> extracted.previousBalance();
            case AMOUNT_DUE -> extracted.amountDue();
            case DUE_DATE -> extracted.dueDate();
            default -> null;
        };
    }

    private static final Map<String, Function<Bill, Object>> FIELD_GETTERS = Map.of(
            PROVIDER_NAME, Bill::getProviderName,
            ACCOUNT_NUMBER, Bill::getAccountNumber,
            PERIOD_START, Bill::getPeriodStart,
            PERIOD_END, Bill::getPeriodEnd,
            CURRENT_CHARGES, b -> scaled(b.getCurrentCharges()),
            PREVIOUS_BALANCE, b -> scaled(b.getPreviousBalance()),
            AMOUNT_DUE, b -> scaled(b.getAmountDue()),
            DUE_DATE, Bill::getDueDate);

    /** Compares amounts by value: 84.5 and 84.50 are the same. */
    private static BigDecimal scaled(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String money(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(Locale.CANADA).format(amount);
    }

    private static String article(UtilityType type) {
        return switch (type) {
            case ELECTRICITY -> "an electricity";
            case GAS -> "a gas";
            case WATER -> "a water";
            case INTERNET -> "an internet";
            case OTHER -> "an other";
        };
    }
}
