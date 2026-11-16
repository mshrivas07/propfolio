package com.propfolio.billing;

/**
 * Invoice lifecycle. OPEN collects a lease's charges as bills are verified (Phase 5A).
 * The remaining statuses are reworked in Phase 5B.
 */
public enum InvoiceStatus {
    OPEN,
    EMAIL_DRAFTED,
    PARTIALLY_PAID,
    PAID,
    VOID
}
