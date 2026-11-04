package com.propfolio.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.propfolio.rentals.UtilityType;

class ExtractedBillTest {

    @Test
    void parsesACompleteReading() {
        ExtractedBill bill = ExtractedBill.fromJson(TestReadings.hydro("1234 5678-9"));

        assertThat(bill.utilityBill()).isTrue();
        assertThat(bill.providerName()).isEqualTo("Sample Hydro Inc.");
        assertThat(bill.accountNumber()).isEqualTo("1234 5678-9");
        assertThat(bill.utilityType()).isEqualTo(UtilityType.ELECTRICITY);
        assertThat(bill.periodStart()).isEqualTo(LocalDate.parse("2026-09-01"));
        assertThat(bill.periodEnd()).isEqualTo(LocalDate.parse("2026-09-30"));
        assertThat(bill.currentCharges()).isEqualByComparingTo("120.00");
        assertThat(bill.currentCharges().scale()).isEqualTo(2);
        assertThat(bill.previousBalance()).isEqualByComparingTo("0");
        assertThat(bill.dueDate()).isEqualTo(LocalDate.parse("2026-10-20"));
        assertThat(bill.confidenceFor(ExtractedBill.CURRENT_CHARGES)).isEqualByComparingTo("0.98");
        assertThat(bill.confidenceFor(ExtractedBill.PERIOD_START)).isEqualByComparingTo("0.98");
    }

    @Test
    void unreadableValuesBecomeNull() {
        String json = TestReadings.reading("  ", null, "STEAM", "Sept 1", "2026-02-30", null, null, null, null, "0.5");

        ExtractedBill bill = ExtractedBill.fromJson(json);

        assertThat(bill.providerName()).isNull();
        assertThat(bill.accountNumber()).isNull();
        assertThat(bill.utilityType()).isNull();
        assertThat(bill.periodStart()).isNull();
        assertThat(bill.periodEnd()).isNull();
        assertThat(bill.currentCharges()).isNull();
    }

    @Test
    void amountsAreRoundedToCentsAndConfidenceIsClamped() {
        String json = TestReadings.reading("A", "1", "WATER", "2026-07-01", "2026-09-30", "84.456", "-10", "74.46",
                "2026-10-15", "1.7");

        ExtractedBill bill = ExtractedBill.fromJson(json);

        assertThat(bill.currentCharges()).isEqualByComparingTo("84.46");
        assertThat(bill.previousBalance()).isEqualByComparingTo("-10.00");
        assertThat(bill.confidenceFor(ExtractedBill.AMOUNT_DUE)).isEqualByComparingTo("1");
    }

    @Test
    void notAUtilityBillIsKept() {
        String json = TestReadings.hydro("1").replace("\"is_utility_bill\": true", "\"is_utility_bill\": false");
        assertThat(ExtractedBill.fromJson(json).utilityBill()).isFalse();
    }

    @Test
    void invalidJsonIsRejectedWithoutEchoingIt() {
        assertThatThrownBy(() -> ExtractedBill.fromJson("account 1234, total $50"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("1234");
    }

    @Test
    void accountNumbersCompareWithoutSpacesDashesOrCase() {
        assertThat(AccountNumbers.same("1234 5678-9", "123456789")).isTrue();
        assertThat(AccountNumbers.same("ab-12", "AB12")).isTrue();
        assertThat(AccountNumbers.same("12345", "12346")).isFalse();
        assertThat(AccountNumbers.same(null, "1")).isFalse();
        assertThat(AccountNumbers.same(" - ", "")).isFalse();
    }
}
