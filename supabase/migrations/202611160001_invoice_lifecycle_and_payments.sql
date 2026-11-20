-- Phase 5B: invoice lifecycle and payments.
--
-- OPEN → INVOICE_CREATED → EMAIL_DRAFTED → SENT → PARTIALLY_PAID → PAID → ARCHIVED, plus CANCELLED.
-- OPEN collects charges; "Create invoice" locks it (INVOICE_CREATED). SENT means nothing received yet.
-- Each step records when it happened. paid_at and voided_at from the first plan are no longer used.

alter table propfolio.invoice add column invoice_created_at timestamptz;
alter table propfolio.invoice add column sent_at timestamptz;
alter table propfolio.invoice add column cancelled_at timestamptz;
alter table propfolio.invoice add column archived_at timestamptz;

-- Carry over anything created under the old statuses (normally there is nothing beyond OPEN).
alter table propfolio.invoice drop constraint invoice_status_valid;
update propfolio.invoice set status = 'CANCELLED', cancelled_at = coalesce(voided_at, updated_at) where status = 'VOID';
update propfolio.invoice set invoice_created_at = created_at where status <> 'OPEN';
update propfolio.invoice set sent_at = coalesce(email_drafted_at, created_at)
  where status in ('PARTIALLY_PAID', 'PAID');

alter table propfolio.invoice drop column paid_at;
alter table propfolio.invoice drop column voided_at;

alter table propfolio.invoice add constraint invoice_status_valid check (status in (
  'OPEN', 'INVOICE_CREATED', 'EMAIL_DRAFTED', 'SENT', 'PARTIALLY_PAID', 'PAID', 'ARCHIVED', 'CANCELLED'));
alter table propfolio.invoice add constraint invoice_created_recorded
  check (status = 'OPEN' or invoice_created_at is not null);
alter table propfolio.invoice add constraint invoice_sent_recorded
  check (status not in ('SENT', 'PARTIALLY_PAID', 'PAID', 'ARCHIVED') or sent_at is not null);
alter table propfolio.invoice add constraint invoice_cancelled_recorded
  check (status <> 'CANCELLED' or cancelled_at is not null);
alter table propfolio.invoice add constraint invoice_archived_recorded
  check (status <> 'ARCHIVED' or archived_at is not null);

-- Payments: one invoice each, amount > 0 (already enforced), short optional note.
alter table propfolio.payment add constraint payment_note_length check (note is null or length(note) <= 500);
