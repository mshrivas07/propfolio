-- =============================================================================
-- Propfolio Phase 4 — bill extraction and verification
--
-- current_charges   this period's charges incl. tax; what invoices split
-- previous_balance  balance carried forward from earlier bills (after payments/credits)
-- amount_due        (existing) total the bill asks for = current + previous, roughly
-- extraction_requested_at / extracted_at / extraction_error  background reading state
--
-- A bill can now only be VERIFIED with current_charges > 0 (instead of amount_due).
-- =============================================================================

alter table propfolio.bill
  add column current_charges         numeric(12, 2),
  add column previous_balance        numeric(12, 2),
  add column extraction_requested_at timestamptz,
  add column extracted_at            timestamptz,
  add column extraction_error        text;

alter table propfolio.bill drop constraint bill_verified_complete;

alter table propfolio.bill
  add constraint bill_verified_complete check (
    status <> 'VERIFIED' or (
      utility_account_id is not null
      and period_start is not null
      and period_end is not null
      and current_charges is not null
      and current_charges > 0
      and verified_at is not null
    )
  );

-- Finding other bills for the same account with overlapping periods.
create index bill_account_period_idx on propfolio.bill (utility_account_id, period_start, period_end);
