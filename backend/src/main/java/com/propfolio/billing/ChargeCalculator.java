package com.propfolio.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import com.propfolio.rentals.ShareType;

/**
 * Works out one charge: a tenant's share of one bill. Pure arithmetic, no database.
 *
 * <pre>
 * days covered = days the lease overlaps the bill period (calendar days, both ends included)
 * PERCENT:  split amount × percent ÷ 100 × days covered ÷ bill period days
 * FLAT:     flat amount × days covered ÷ bill period days        (a flat amount is per bill)
 * </pre>
 *
 * The result is rounded once, to the cent, half up. Days outside any lease (vacancy) are simply
 * not charged; the landlord absorbs them.
 */
public final class ChargeCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int WORKING_SCALE = 10;

    private ChargeCalculator() {
    }

    /** One computed charge. */
    public record Charge(LocalDate coveredStart, LocalDate coveredEnd, int daysCovered, int billPeriodDays,
            BigDecimal amount) {
    }

    /**
     * @param billStart   first day of the bill period
     * @param billEnd     last day of the bill period (on or after billStart)
     * @param splitAmount the bill's amount to split (used for PERCENT only)
     * @param leaseStart  first day of the lease
     * @param leaseEnd    last day of the lease, or null if ongoing
     * @return the charge, or empty when the lease doesn't overlap the bill period
     */
    public static Optional<Charge> calculate(LocalDate billStart, LocalDate billEnd, BigDecimal splitAmount,
            LocalDate leaseStart, LocalDate leaseEnd, ShareType shareType, BigDecimal shareValue) {
        if (billEnd.isBefore(billStart)) {
            throw new IllegalArgumentException("Bill period ends before it starts");
        }
        LocalDate coveredStart = leaseStart.isAfter(billStart) ? leaseStart : billStart;
        LocalDate coveredEnd = leaseEnd == null || leaseEnd.isAfter(billEnd) ? billEnd : leaseEnd;
        if (coveredEnd.isBefore(coveredStart)) {
            return Optional.empty();
        }
        int periodDays = days(billStart, billEnd);
        int daysCovered = days(coveredStart, coveredEnd);

        BigDecimal base = shareType == ShareType.PERCENT
                ? splitAmount.multiply(shareValue).divide(HUNDRED, WORKING_SCALE, RoundingMode.HALF_UP)
                : shareValue;
        BigDecimal amount = base.multiply(BigDecimal.valueOf(daysCovered))
                .divide(BigDecimal.valueOf(periodDays), 2, RoundingMode.HALF_UP);
        return Optional.of(new Charge(coveredStart, coveredEnd, daysCovered, periodDays, amount));
    }

    /** Calendar days from start to end, both included. */
    static int days(LocalDate start, LocalDate end) {
        return Math.toIntExact(ChronoUnit.DAYS.between(start, end) + 1);
    }
}
