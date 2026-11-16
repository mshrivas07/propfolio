package com.propfolio.documents;

/**
 * Made-up reader answers in the shape the model returns (snake_case, see utility-bill-schema.json).
 * Never real bill data.
 */
public final class TestReadings {

    private TestReadings() {
    }

    /** A clean September hydro bill: 30 days, $120.00 current, $0 previous, all fields 0.98 sure. */
    public static String hydro(String accountNumber) {
        return reading("Sample Hydro Inc.", accountNumber, "ELECTRICITY", "2026-09-01", "2026-09-30",
                "120.00", "0.00", "120.00", "2026-10-20", "0.98");
    }

    public static String reading(String provider, String accountNumber, String type, String start, String end,
            String current, String previous, String due, String dueDate, String confidence) {
        return """
                {
                  "is_utility_bill": true,
                  "provider_name": %s,
                  "account_number": %s,
                  "service_address": "12 Sample St, Testville ON",
                  "utility_type": %s,
                  "period_start": %s,
                  "period_end": %s,
                  "current_charges": %s,
                  "previous_balance": %s,
                  "amount_due": %s,
                  "due_date": %s,
                  "confidence": {
                    "provider_name": %10$s, "account_number": %10$s, "service_address": %10$s,
                    "utility_type": %10$s, "period_start": %10$s, "period_end": %10$s,
                    "current_charges": %10$s, "previous_balance": %10$s, "amount_due": %10$s, "due_date": %10$s
                  }
                }
                """.formatted(q(provider), q(accountNumber), q(type), q(start), q(end), n(current), n(previous),
                n(due), q(dueDate), confidence);
    }

    private static String q(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private static String n(String value) {
        return value == null ? "null" : value;
    }
}
