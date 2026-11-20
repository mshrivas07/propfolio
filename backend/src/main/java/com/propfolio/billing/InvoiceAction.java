package com.propfolio.billing;

/**
 * Everything that can be done to an invoice. {@link InvoiceRules} says which are allowed in which status;
 * responses list the allowed ones so the screen doesn't repeat the rules.
 */
public enum InvoiceAction {
    /** OPEN → INVOICE_CREATED: freeze the charges and total. */
    CREATE_INVOICE,
    /** Undo "Create invoice" (before SENT only): back to OPEN with the same number. */
    DELETE,
    /** Create or replace the Gmail draft (Phase 6). */
    CREATE_EMAIL_DRAFT,
    MARK_SENT,
    RECORD_PAYMENT,
    DELETE_PAYMENT,
    /** SENT by mistake, nothing received: CANCELLED, charges go back to the OPEN invoice. */
    CANCEL,
    ARCHIVE,
    UNARCHIVE,
    EDIT_DUE_DATE
}
