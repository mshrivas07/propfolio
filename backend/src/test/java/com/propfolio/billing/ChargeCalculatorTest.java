package com.propfolio.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.propfolio.rentals.ShareType;

/**
 * The charge arithmetic, including Bharti's real Van Scott September rows from her spreadsheet.
 */
class ChargeCalculatorTest {

    @Test
    void vanScottEnbridgeSeptember() {
        // $124, Aug 26 – Sep 23 (29 days), tenant from Sep 1 (23 days), 70%
        assertCharge(percent("124.00", "2026-08-26", "2026-09-23", "2026-09-01", null, "70"), "68.84", 23, 29);
    }

    @Test
    void vanScottAlectraSeptember() {
        // $220.79, Aug 19 – Sep 17 (30 days), tenant from Sep 2 (16 days), 70%
        assertCharge(percent("220.79", "2026-08-19", "2026-09-17", "2026-09-02", null, "70"), "82.43", 16, 30);
    }

    @Test
    void vanScottRogersSeptember() {
        // $90.40, Aug 22 – Sep 21 (31 days), tenant from Sep 1 (21 days), 70%
        assertCharge(percent("90.40", "2026-08-22", "2026-09-21", "2026-09-01", null, "70"), "42.87", 21, 31);
    }

    @Test
    void deforestHydroFullPeriodAt30Percent() {
        assertCharge(percent("337.04", "2026-09-01", "2026-09-30", "2026-01-01", null, "30"), "101.11", 30, 30);
    }

    @Test
    void moveOutMidPeriod() {
        // $300, Sep 1–30, lease ends Sep 15 → 15 of 30 days at 100%
        assertCharge(percent("300.00", "2026-09-01", "2026-09-30", "2026-01-01", "2026-09-15", "100"), "150.00", 15, 30);
    }

    @Test
    void twoLeasesInOneBillShareItWithoutGapsOrOverlap() {
        Optional<ChargeCalculator.Charge> first = percent("300.00", "2026-09-01", "2026-09-30", "2026-01-01", "2026-09-15", "100");
        Optional<ChargeCalculator.Charge> second = percent("300.00", "2026-09-01", "2026-09-30", "2026-09-16", null, "100");

        assertThat(first.orElseThrow().coveredEnd()).isEqualTo(LocalDate.parse("2026-09-15"));
        assertThat(second.orElseThrow().coveredStart()).isEqualTo(LocalDate.parse("2026-09-16"));
        assertThat(first.get().amount().add(second.get().amount())).isEqualByComparingTo("300.00");
    }

    @Test
    void leaseOutsideTheBillPeriodGetsNoCharge() {
        assertThat(percent("100.00", "2026-09-01", "2026-09-30", "2026-10-01", null, "70")).isEmpty();
        assertThat(percent("100.00", "2026-09-01", "2026-09-30", "2026-01-01", "2026-08-31", "70")).isEmpty();
    }

    @Test
    void sameDayLeaseIsOneDay() {
        assertCharge(percent("310.00", "2026-10-01", "2026-10-31", "2026-10-15", "2026-10-15", "100"), "10.00", 1, 31);
    }

    @Test
    void oneDayBill() {
        assertCharge(percent("12.34", "2026-10-01", "2026-10-01", "2026-01-01", null, "50"), "6.17", 1, 1);
    }

    @Test
    void quarterlyBillOf92Days() {
        // Jul 1 – Sep 30 = 92 days; tenant from Aug 1 = 61 days; $184 at 70% → 85.40
        assertCharge(percent("184.00", "2026-07-01", "2026-09-30", "2026-08-01", null, "70"), "85.40", 61, 92);
    }

    @Test
    void flatAmountIsPerBillAndProrated() {
        // $50 flat internet, bill Sep 2 – Oct 1 (30 days), tenant present 15 days → $25
        Optional<ChargeCalculator.Charge> charge = ChargeCalculator.calculate(LocalDate.parse("2026-09-02"),
                LocalDate.parse("2026-10-01"), new BigDecimal("80.00"), LocalDate.parse("2026-09-17"), null,
                ShareType.FLAT, new BigDecimal("50"));
        assertCharge(charge, "25.00", 15, 30);
    }

    @Test
    void roundsOnceHalfUp() {
        // 100 × 33.333% × 1/3 = 11.111 → 11.11; exactly half a cent (0.01 × 1/2 = 0.005) rounds up
        assertCharge(percent("100.00", "2026-09-01", "2026-09-03", "2026-09-03", null, "33.333"), "11.11", 1, 3);
        assertCharge(percent("0.01", "2026-09-01", "2026-09-02", "2026-09-02", null, "100"), "0.01", 1, 2);
    }

    @Test
    void billPeriodEndingBeforeItStartsIsRejected() {
        assertThatThrownBy(() -> percent("1", "2026-09-30", "2026-09-01", "2026-01-01", null, "50"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invoiceNumbersArePaddedToFourDigits() {
        assertThat(InvoiceNumberService.format(2026, 7)).isEqualTo("INV-2026-0007");
        assertThat(InvoiceNumberService.format(2026, 12345)).isEqualTo("INV-2026-12345");
    }

    private static Optional<ChargeCalculator.Charge> percent(String amount, String billStart, String billEnd,
            String leaseStart, String leaseEnd, String percent) {
        return ChargeCalculator.calculate(LocalDate.parse(billStart), LocalDate.parse(billEnd), new BigDecimal(amount),
                LocalDate.parse(leaseStart), leaseEnd == null ? null : LocalDate.parse(leaseEnd), ShareType.PERCENT,
                new BigDecimal(percent));
    }

    private static void assertCharge(Optional<ChargeCalculator.Charge> charge, String amount, int days, int periodDays) {
        assertThat(charge).isPresent();
        assertThat(charge.get().amount()).isEqualByComparingTo(amount);
        assertThat(charge.get().amount().scale()).isEqualTo(2);
        assertThat(charge.get().daysCovered()).isEqualTo(days);
        assertThat(charge.get().billPeriodDays()).isEqualTo(periodDays);
    }
}
