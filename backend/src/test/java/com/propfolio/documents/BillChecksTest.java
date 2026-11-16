package com.propfolio.documents;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.propfolio.documents.web.BillDtos.ReviewIssue;
import com.propfolio.rentals.UtilityAccount;
import com.propfolio.rentals.UtilityType;

/**
 * The review rules, without a database. Each test starts from a clean bill that is READY
 * and breaks one thing.
 */
class BillChecksTest {

    private static final BigDecimal THRESHOLD = new BigDecimal("0.85");
    private static final UUID LANDLORD = UUID.randomUUID();

    private final UtilityAccount hydro = account(UtilityType.ELECTRICITY, "Sample Hydro", "123456789");

    @Test
    void aCleanBillIsReady() {
        BillChecks.Result result = evaluate(cleanBill(), extracted(TestReadings.hydro("1234 5678-9")), hydro);

        assertThat(result.issues()).isEmpty();
        assertThat(result.ready()).isTrue();
        assertThat(result.confidence()).isEqualByComparingTo("0.980");
    }

    @Test
    void missingPeriodIsAnIssue() {
        Bill bill = cleanBill();
        bill.setPeriodStart(null);
        assertIssue(evaluate(bill, null, hydro), ExtractedBill.PERIOD_START, "first day");
    }

    @Test
    void periodEndingBeforeItStartsIsAnIssue() {
        Bill bill = cleanBill();
        bill.setPeriodEnd(LocalDate.parse("2026-08-31"));
        assertIssue(evaluate(bill, null, hydro), ExtractedBill.PERIOD_END, "ends before it starts");
    }

    @Test
    void periodOver120DaysIsAnIssueButExactly120IsFine() {
        Bill bill = cleanBill();
        bill.setPeriodStart(LocalDate.parse("2026-06-03"));
        bill.setPeriodEnd(LocalDate.parse("2026-09-30")); // 120 days inclusive
        assertThat(evaluate(bill, null, hydro).issues()).isEmpty();

        bill.setPeriodStart(LocalDate.parse("2026-06-02")); // 121 days
        assertIssue(evaluate(bill, null, hydro), ExtractedBill.PERIOD_END, "121 days");
    }

    @Test
    void zeroOrMissingCurrentChargesIsAnIssue() {
        Bill bill = cleanBill();
        bill.setCurrentCharges(BigDecimal.ZERO);
        bill.setAmountDue(BigDecimal.ZERO);
        assertIssue(evaluate(bill, null, hydro), ExtractedBill.CURRENT_CHARGES, "above $0");

        bill.setCurrentCharges(null);
        assertIssue(evaluate(bill, null, hydro), ExtractedBill.CURRENT_CHARGES, "Enter this period");
    }

    @Test
    void amountDueMustRoughlyEqualCurrentPlusPrevious() {
        Bill bill = cleanBill();
        bill.setPreviousBalance(new BigDecimal("30.00"));
        bill.setAmountDue(new BigDecimal("150.04")); // within 5 cents of 150
        assertThat(evaluate(bill, null, hydro).issues()).isEmpty();

        bill.setAmountDue(new BigDecimal("180.00"));
        assertIssue(evaluate(bill, null, hydro), ExtractedBill.AMOUNT_DUE, "isn't current charges");
    }

    @Test
    void accountNumberDifferentFromTheLinkedAccountIsAnIssue() {
        Bill bill = cleanBill();
        bill.setAccountNumber("999-999");
        assertIssue(evaluate(bill, null, hydro), ExtractedBill.ACCOUNT_NUMBER, "doesn't match");
    }

    @Test
    void noLinkedAccountIsAnIssueNamingTheNumber() {
        BillChecks.Result result = evaluate(cleanBill(), null, null);
        assertIssue(result, "utilityAccountId", "1234 5678-9");
        assertThat(result.ready()).isFalse();
    }

    @Test
    void billTypeDifferentFromTheAccountTypeIsAnIssue() {
        String gasReading = TestReadings.hydro("1234 5678-9").replace("\"ELECTRICITY\"", "\"GAS\"");
        assertIssue(evaluate(cleanBill(), extracted(gasReading), hydro), "utilityAccountId", "a gas bill");
    }

    @Test
    void differentProviderIsAnIssueButSameCompanyWithSuffixIsNot() {
        Bill bill = cleanBill();
        bill.setProviderName("Sample Hydro Utilities Inc.");
        assertThat(evaluate(bill, null, hydro).issues()).isEmpty();

        bill.setProviderName("Other Gas Co");
        assertIssue(evaluate(bill, null, hydro), ExtractedBill.PROVIDER_NAME, "doesn't look like");
    }

    @Test
    void overlappingBillOnTheSameAccountIsAnIssue() {
        Bill other = new Bill(LANDLORD, "hydro-copy.pdf", "x", "application/pdf", 1, "sha-other");
        other.setPeriodStart(LocalDate.parse("2026-09-15"));
        other.setPeriodEnd(LocalDate.parse("2026-10-14"));

        BillChecks.Result result = BillChecks.evaluate(cleanBill(), null, hydro, List.of(other), THRESHOLD);

        assertIssue(result, ExtractedBill.PERIOD_START, "hydro-copy.pdf");
    }

    @Test
    void lowConfidenceFieldIsFlaggedUntilTheLandlordChangesIt() {
        String unsure = TestReadings.hydro("1234 5678-9")
                .replace("\"current_charges\": 0.98", "\"current_charges\": 0.40");
        Bill bill = cleanBill();

        BillChecks.Result before = evaluate(bill, extracted(unsure), hydro);
        assertIssue(before, ExtractedBill.CURRENT_CHARGES, "only 40% sure");
        assertThat(before.fieldReview().get(ExtractedBill.CURRENT_CHARGES).lowConfidence()).isTrue();
        assertThat(before.ready()).isFalse();

        bill.setCurrentCharges(new BigDecimal("125.00"));
        bill.setAmountDue(new BigDecimal("125.00"));
        BillChecks.Result after = evaluate(bill, extracted(unsure), hydro);
        assertThat(after.fieldReview().get(ExtractedBill.CURRENT_CHARGES).edited()).isTrue();
        assertThat(after.issues()).isEmpty();
        assertThat(after.ready()).isTrue();
    }

    @Test
    void sameAmountWithDifferentScaleDoesNotCountAsAnEdit() {
        Bill bill = cleanBill();
        bill.setCurrentCharges(new BigDecimal("120")); // reader said 120.00
        BillChecks.Result result = evaluate(bill, extracted(TestReadings.hydro("1234 5678-9")), hydro);
        assertThat(result.fieldReview().get(ExtractedBill.CURRENT_CHARGES).edited()).isFalse();
    }

    @Test
    void notAUtilityBillIsAnIssue() {
        String json = TestReadings.hydro("1234 5678-9").replace("\"is_utility_bill\": true", "\"is_utility_bill\": false");
        assertIssue(evaluate(cleanBill(), extracted(json), hydro), null, "doesn't look like a utility bill");
    }

    @Test
    void eachFailedRuleLowersConfidence() {
        Bill bill = cleanBill();
        bill.setAccountNumber("999");             // 1 failure
        bill.setProviderName("Other Gas Co");     // 2 failures
        BillChecks.Result result = evaluate(bill, null, hydro);
        // hand-typed fields count as 1.0; 1.0 × (1 − 2 × 0.15) = 0.70
        assertThat(result.confidence()).isEqualByComparingTo("0.700");
        assertThat(result.ready()).isFalse();
    }

    /** A bill whose values exactly match {@link TestReadings#hydro}. */
    private static Bill cleanBill() {
        Bill bill = new Bill(LANDLORD, "hydro.pdf", "x/hydro.pdf", "application/pdf", 10, "sha");
        bill.setProviderName("Sample Hydro Inc.");
        bill.setAccountNumber("1234 5678-9");
        bill.setPeriodStart(LocalDate.parse("2026-09-01"));
        bill.setPeriodEnd(LocalDate.parse("2026-09-30"));
        bill.setCurrentCharges(new BigDecimal("120.00"));
        bill.setPreviousBalance(new BigDecimal("0.00"));
        bill.setAmountDue(new BigDecimal("120.00"));
        bill.setDueDate(LocalDate.parse("2026-10-20"));
        return bill;
    }

    private static UtilityAccount account(UtilityType type, String provider, String number) {
        UtilityAccount account = new UtilityAccount(LANDLORD, UUID.randomUUID(), type, provider);
        account.setAccountNumber(number);
        return account;
    }

    private static ExtractedBill extracted(String json) {
        return ExtractedBill.fromJson(json);
    }

    private static BillChecks.Result evaluate(Bill bill, ExtractedBill extracted, UtilityAccount account) {
        return BillChecks.evaluate(bill, extracted, account, List.of(), THRESHOLD);
    }

    private static void assertIssue(BillChecks.Result result, String field, String messagePart) {
        assertThat(result.issues())
                .as("issues: %s", result.issues())
                .anySatisfy(issue -> {
                    assertThat(issue.field()).isEqualTo(field);
                    assertThat(issue.message()).contains(messagePart);
                });
        assertThat(result.ready()).isFalse();
        assertThat(result.issues()).extracting(ReviewIssue::message).doesNotContainNull();
    }
}
