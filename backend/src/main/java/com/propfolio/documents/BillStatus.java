package com.propfolio.documents;

/**
 * Bill lifecycle. Only VERIFIED bills feed invoices.
 */
public enum BillStatus {
    UPLOADED,
    EXTRACTED,
    READY,
    NEEDS_REVIEW,
    VERIFIED
}
