package com.propfolio.billing;

import static com.propfolio.billing.InvoiceStatus.ARCHIVED;
import static com.propfolio.billing.InvoiceStatus.EMAIL_DRAFTED;
import static com.propfolio.billing.InvoiceStatus.INVOICE_CREATED;
import static com.propfolio.billing.InvoiceStatus.OPEN;
import static com.propfolio.billing.InvoiceStatus.PAID;
import static com.propfolio.billing.InvoiceStatus.PARTIALLY_PAID;
import static com.propfolio.billing.InvoiceStatus.SENT;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.propfolio.common.error.ConflictException;

/**
 * The one place that says what can be done to an invoice in each status. Pure, no database.
 */
public final class InvoiceRules {

    private static final Map<InvoiceAction, Set<InvoiceStatus>> ALLOWED_FROM = Map.of(
            InvoiceAction.CREATE_INVOICE, EnumSet.of(OPEN),
            InvoiceAction.DELETE, EnumSet.of(INVOICE_CREATED, EMAIL_DRAFTED),
            InvoiceAction.CREATE_EMAIL_DRAFT, EnumSet.of(INVOICE_CREATED, EMAIL_DRAFTED),
            InvoiceAction.MARK_SENT, EnumSet.of(EMAIL_DRAFTED),
            InvoiceAction.RECORD_PAYMENT, EnumSet.of(SENT, PARTIALLY_PAID),
            InvoiceAction.DELETE_PAYMENT, EnumSet.of(PARTIALLY_PAID, PAID),
            // PARTIALLY_PAID becomes SENT again once its payments are deleted, so SENT covers that case.
            InvoiceAction.CANCEL, EnumSet.of(SENT),
            InvoiceAction.ARCHIVE, EnumSet.of(PAID),
            InvoiceAction.UNARCHIVE, EnumSet.of(ARCHIVED),
            InvoiceAction.EDIT_DUE_DATE, EnumSet.of(OPEN, INVOICE_CREATED));

    private static final Map<InvoiceAction, String> REFUSALS = Map.of(
            InvoiceAction.CREATE_INVOICE, "Only an open invoice with charges can be created.",
            InvoiceAction.DELETE, "An invoice can be deleted only before it's sent. Cancel it instead.",
            InvoiceAction.CREATE_EMAIL_DRAFT, "An email draft can be made only after the invoice is created and before it's sent.",
            InvoiceAction.MARK_SENT, "Create the email draft before marking the invoice as sent.",
            InvoiceAction.RECORD_PAYMENT, "Payments can be recorded once the invoice is sent, until it's paid.",
            InvoiceAction.DELETE_PAYMENT, "This invoice's payments can't be changed now.",
            InvoiceAction.CANCEL, "Only a sent invoice with no payments can be cancelled. Delete its payments first.",
            InvoiceAction.ARCHIVE, "Only a paid invoice can be archived.",
            InvoiceAction.UNARCHIVE, "Only an archived invoice can be unarchived.",
            InvoiceAction.EDIT_DUE_DATE, "The due date can be changed only before the email draft is made.");

    /** Statuses that count toward what the tenant owes. */
    private static final Set<InvoiceStatus> OWED = EnumSet.of(INVOICE_CREATED, EMAIL_DRAFTED, SENT, PARTIALLY_PAID,
            PAID, ARCHIVED);

    private InvoiceRules() {
    }

    public static boolean allowed(InvoiceAction action, InvoiceStatus status, int chargeCount) {
        if (action == InvoiceAction.CREATE_INVOICE && chargeCount == 0) {
            return false;
        }
        return ALLOWED_FROM.get(action).contains(status);
    }

    /** The actions allowed now, in a stable order. */
    public static List<InvoiceAction> available(InvoiceStatus status, int chargeCount) {
        return EnumSet.allOf(InvoiceAction.class).stream()
                .filter(action -> allowed(action, status, chargeCount))
                .toList();
    }

    /** @throws ConflictException (409) with a plain explanation when the action isn't allowed */
    public static void require(InvoiceAction action, InvoiceStatus status, int chargeCount) {
        if (!allowed(action, status, chargeCount)) {
            throw new ConflictException(REFUSALS.get(action));
        }
    }

    /** True if the invoice counts toward the tenant's outstanding balance (created, not cancelled). */
    public static boolean countsAsOwed(InvoiceStatus status) {
        return OWED.contains(status);
    }

    /** Status after payments change: nothing received → SENT, some → PARTIALLY_PAID, all → PAID. */
    public static InvoiceStatus statusForReceived(BigDecimal received, BigDecimal total) {
        if (received.signum() == 0) {
            return SENT;
        }
        return received.compareTo(total) >= 0 ? PAID : PARTIALLY_PAID;
    }
}
