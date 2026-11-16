-- =============================================================================
-- Propfolio Phase 5A — charges and open (collecting) invoices
--
-- utility_account.equal_payment_plan  tenants pay a share of the plan payment (amount_due)
-- bill.split_amount                   the amount split with tenants, fixed when the bill is verified
-- invoice status OPEN                 the invoice collecting a lease's charges (replaces DRAFT);
--                                     an empty OPEN invoice has no period yet
-- =============================================================================

alter table propfolio.utility_account
  add column equal_payment_plan boolean not null default false;

-- Split amount ---------------------------------------------------------------
alter table propfolio.bill add column split_amount numeric(12, 2);

-- Bills verified before this migration split their current charges (the Phase 4 rule).
update propfolio.bill
   set split_amount = current_charges
 where status = 'VERIFIED' and split_amount is null;

alter table propfolio.bill drop constraint bill_verified_complete;
alter table propfolio.bill
  add constraint bill_verified_complete check (
    status <> 'VERIFIED' or (
      utility_account_id is not null
      and period_start is not null
      and period_end is not null
      and split_amount is not null
      and split_amount > 0
      and verified_at is not null
    )
  );

-- Invoices -------------------------------------------------------------------
-- No invoices exist yet; rename any DRAFT just in case, then switch the status list.
alter table propfolio.invoice drop constraint invoice_status_valid;
update propfolio.invoice set status = 'OPEN' where status = 'DRAFT';
alter table propfolio.invoice alter column status set default 'OPEN';
alter table propfolio.invoice
  add constraint invoice_status_valid
    check (status in ('OPEN', 'EMAIL_DRAFTED', 'PARTIALLY_PAID', 'PAID', 'VOID'));

-- An OPEN invoice with no charges yet has no period; every other invoice must have one.
alter table propfolio.invoice alter column period_start drop not null;
alter table propfolio.invoice alter column period_end drop not null;
alter table propfolio.invoice
  add constraint invoice_period_required
    check (status = 'OPEN' or (period_start is not null and period_end is not null));

-- At most one OPEN invoice per lease.
create unique index invoice_one_open_per_lease on propfolio.invoice (lease_id) where status = 'OPEN';

-- Finding a lease's lines and verified bills quickly.
create index invoice_line_bill_unit_idx on propfolio.invoice_line (bill_id, unit_id);
