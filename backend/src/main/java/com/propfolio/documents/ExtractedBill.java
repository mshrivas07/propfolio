package com.propfolio.documents;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import com.propfolio.rentals.UtilityType;

/**
 * What the reader (OpenAI) found on a bill. Stored as JSON in {@code bill.extraction_json}
 * exactly as the model returned it (snake_case keys, see resources/extraction/utility-bill-schema.json).
 *
 * <p>{@code onPaymentPlan} is null when the bill (or an older reading) doesn't say.
 *
 * <p>Parsing is lenient: a value that can't be understood (bad date, unknown type) becomes null
 * and is then flagged during review, rather than failing the whole bill.
 */
public record ExtractedBill(
        boolean utilityBill,
        String providerName,
        String accountNumber,
        String serviceAddress,
        UtilityType utilityType,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal currentCharges,
        BigDecimal previousBalance,
        BigDecimal amountDue,
        LocalDate dueDate,
        Boolean onPaymentPlan,
        Map<String, BigDecimal> confidence) {

    /** Field names used in API responses, review issues and the confidence map. */
    public static final String PROVIDER_NAME = "providerName";
    public static final String ACCOUNT_NUMBER = "accountNumber";
    public static final String UTILITY_TYPE = "utilityType";
    public static final String SERVICE_ADDRESS = "serviceAddress";
    public static final String PERIOD_START = "periodStart";
    public static final String PERIOD_END = "periodEnd";
    public static final String CURRENT_CHARGES = "currentCharges";
    public static final String PREVIOUS_BALANCE = "previousBalance";
    public static final String AMOUNT_DUE = "amountDue";
    public static final String DUE_DATE = "dueDate";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final Map<String, String> CONFIDENCE_KEYS = Map.of(
            "provider_name", PROVIDER_NAME,
            "account_number", ACCOUNT_NUMBER,
            "utility_type", UTILITY_TYPE,
            "service_address", SERVICE_ADDRESS,
            "period_start", PERIOD_START,
            "period_end", PERIOD_END,
            "current_charges", CURRENT_CHARGES,
            "previous_balance", PREVIOUS_BALANCE,
            "amount_due", AMOUNT_DUE,
            "due_date", DUE_DATE);

    /** Confidence (0–1) for a field, or null if the reader didn't report one. */
    public BigDecimal confidenceFor(String field) {
        return confidence.get(field);
    }

    /**
     * Parses the model's JSON output.
     *
     * @throws IllegalArgumentException if the text isn't a JSON object at all
     */
    @SuppressWarnings("unchecked")
    public static ExtractedBill fromJson(String json) {
        Map<String, Object> root;
        try {
            root = JSON.readValue(json, Map.class);
        } catch (JacksonException e) {
            // Never include the text itself: it is bill content.
            throw new IllegalArgumentException("The reader's answer wasn't valid JSON");
        }
        if (root == null) {
            throw new IllegalArgumentException("The reader's answer was empty");
        }

        Map<String, BigDecimal> confidence = new HashMap<>();
        if (root.get("confidence") instanceof Map<?, ?> scores) {
            scores.forEach((key, value) -> {
                String field = CONFIDENCE_KEYS.get(String.valueOf(key));
                BigDecimal score = number(value);
                if (field != null && score != null) {
                    confidence.put(field, clamp(score));
                }
            });
        }

        return new ExtractedBill(
                !Boolean.FALSE.equals(root.get("is_utility_bill")),
                text(root.get("provider_name")),
                text(root.get("account_number")),
                text(root.get("service_address")),
                utilityType(root.get("utility_type")),
                date(root.get("period_start")),
                date(root.get("period_end")),
                money(root.get("current_charges")),
                money(root.get("previous_balance")),
                money(root.get("amount_due")),
                date(root.get("due_date")),
                root.get("on_payment_plan") instanceof Boolean plan ? plan : null,
                Map.copyOf(confidence));
    }

    private static String text(Object value) {
        if (!(value instanceof String s)) {
            return null;
        }
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static LocalDate date(Object value) {
        String s = text(value);
        if (s == null) {
            return null;
        }
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static UtilityType utilityType(Object value) {
        String s = text(value);
        if (s == null) {
            return null;
        }
        try {
            return UtilityType.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static BigDecimal money(Object value) {
        BigDecimal number = number(value);
        return number == null ? null : number.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal number(Object value) {
        if (value instanceof BigDecimal d) {
            return d;
        }
        if (value instanceof Double || value instanceof Float) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.longValue());
        }
        if (value instanceof String s) {
            try {
                return new BigDecimal(s.replace("$", "").replace(",", "").trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static BigDecimal clamp(BigDecimal score) {
        BigDecimal clamped = score.max(BigDecimal.ZERO).min(BigDecimal.ONE);
        return clamped.setScale(3, RoundingMode.HALF_UP);
    }
}
