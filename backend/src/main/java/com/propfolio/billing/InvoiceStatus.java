package com.propfolio.billing;

/**
 * Invoice lifecycle (Phase 5B):
 *
 * <pre>
 * OPEN ──Create invoice──▶ INVOICE_CREATED ──draft──▶ EMAIL_DRAFTED ──Mark as sent──▶ SENT
 *   ▲                            │                        │  ▲ (draft again)          │ payment
 *   └──────── Delete (undo) ─────┴────────────────────────┘                           ▼
 *                                                   PAID ◀──payment── PARTIALLY_PAID
 *                                                    │
 *                                                 Archive ⇄ Unarchive ── ARCHIVED
 * SENT ──Cancel invoice (mistakes only, no payments)──▶ CANCELLED
 * </pre>
 *
 * The rules for every move live in {@link InvoiceRules}.
 */
public enum InvoiceStatus {
    /** Collecting charges as bills are verified. One per lease. */
    OPEN,
    /** Locked: charges and total frozen. Counts toward the tenant's outstanding balance from here on. */
    INVOICE_CREATED,
    /** A Gmail draft exists (Phase 6). */
    EMAIL_DRAFTED,
    /** Sent to the tenant; nothing received yet. */
    SENT,
    PARTIALLY_PAID,
    PAID,
    /** Paid and hidden from the default list. */
    ARCHIVED,
    /** Sent by mistake; kept for the record. Its charges went back to the OPEN invoice. */
    CANCELLED
}
