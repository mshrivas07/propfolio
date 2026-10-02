package com.propfolio.billing;

/**
 * Invoice lifecycle. An invoice is locked once EMAIL_DRAFTED.
 */
public enum InvoiceStatus {
    DRAFT,
    EMAIL_DRAFTED,
    PARTIALLY_PAID,
    PAID,
    VOID
}
