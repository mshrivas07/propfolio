-- =============================================================================
-- Propfolio MVP1 — initial schema
--
-- All app tables live in their own schema, "propfolio", which is NOT exposed by
-- Supabase's auto-generated Data API. Only the Spring Boot backend (connecting as
-- the postgres role) reads and writes these tables. The browser never queries them.
--
-- Status / type columns use text + CHECK constraints (not Postgres enums) so new
-- values can be added later with a simple constraint change.
--
-- Run once in the Supabase SQL Editor. Safe to re-run only on an empty schema.
-- =============================================================================

-- btree_gist lets the double-billing EXCLUDE constraint compare uuids with "=".
create extension if not exists btree_gist with schema extensions;

create schema if not exists propfolio;

-- Keep Supabase's public API roles out of this schema entirely.
revoke all on schema propfolio from public;
revoke all on schema propfolio from anon, authenticated;

-- -----------------------------------------------------------------------------
-- updated_at maintenance
-- -----------------------------------------------------------------------------
create or replace function propfolio.set_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

-- -----------------------------------------------------------------------------
-- landlord: one profile row per Supabase auth user (id = auth.users.id)
-- -----------------------------------------------------------------------------
create table propfolio.landlord (
  id            uuid primary key references auth.users (id) on delete cascade,
  email         text,
  display_name  text,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);

-- -----------------------------------------------------------------------------
-- Rentals
-- -----------------------------------------------------------------------------
create table propfolio.property (
  id            uuid primary key default gen_random_uuid(),
  landlord_id   uuid not null references propfolio.landlord (id) on delete cascade,
  name          text not null,
  address_line1 text,
  address_line2 text,
  city          text,
  province      text,
  postal_code   text,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now(),
  constraint property_name_unique unique (landlord_id, name)
);
create index property_landlord_idx on propfolio.property (landlord_id);

create table propfolio.unit (
  id            uuid primary key default gen_random_uuid(),
  landlord_id   uuid not null references propfolio.landlord (id) on delete cascade,
  property_id   uuid not null references propfolio.property (id) on delete restrict,
  name          text not null,
  notes         text,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now(),
  constraint unit_name_unique unique (property_id, name)
);
create index unit_landlord_idx on propfolio.unit (landlord_id);
create index unit_property_idx on propfolio.unit (property_id);

create table propfolio.tenant (
  id            uuid primary key default gen_random_uuid(),
  landlord_id   uuid not null references propfolio.landlord (id) on delete cascade,
  full_name     text not null,
  emails        text[] not null default '{}',
  phone         text,
  notes         text,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);
create index tenant_landlord_idx on propfolio.tenant (landlord_id);

-- A lease links a tenant to a unit for a date range; it drives proration.
create table propfolio.lease (
  id            uuid primary key default gen_random_uuid(),
  landlord_id   uuid not null references propfolio.landlord (id) on delete cascade,
  unit_id       uuid not null references propfolio.unit (id) on delete restrict,
  tenant_id     uuid not null references propfolio.tenant (id) on delete restrict,
  start_date    date not null,
  end_date      date,                       -- null = ongoing
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now(),
  constraint lease_dates_valid check (end_date is null or end_date >= start_date)
);
create index lease_landlord_idx on propfolio.lease (landlord_id);
create index lease_unit_idx on propfolio.lease (unit_id);
create index lease_tenant_idx on propfolio.lease (tenant_id);

create table propfolio.utility_account (
  id             uuid primary key default gen_random_uuid(),
  landlord_id    uuid not null references propfolio.landlord (id) on delete cascade,
  property_id    uuid not null references propfolio.property (id) on delete restrict,
  utility_type   text not null,
  provider_name  text not null,
  account_number text,
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now(),
  constraint utility_account_type_valid
    check (utility_type in ('ELECTRICITY', 'GAS', 'WATER', 'INTERNET', 'OTHER'))
);
create index utility_account_landlord_idx on propfolio.utility_account (landlord_id);
create index utility_account_property_idx on propfolio.utility_account (property_id);
create index utility_account_number_idx on propfolio.utility_account (landlord_id, account_number);

-- One rule per unit per utility account: PERCENT (0 < value <= 100) or FLAT amount.
create table propfolio.split_rule (
  id                 uuid primary key default gen_random_uuid(),
  landlord_id        uuid not null references propfolio.landlord (id) on delete cascade,
  unit_id            uuid not null references propfolio.unit (id) on delete cascade,
  utility_account_id uuid not null references propfolio.utility_account (id) on delete cascade,
  share_type         text not null,
  share_value        numeric(12, 4) not null,
  created_at         timestamptz not null default now(),
  updated_at         timestamptz not null default now(),
  constraint split_rule_unique unique (unit_id, utility_account_id),
  constraint split_rule_type_valid check (share_type in ('PERCENT', 'FLAT')),
  constraint split_rule_value_valid check (
    share_value > 0 and (share_type <> 'PERCENT' or share_value <= 100)
  )
);
create index split_rule_landlord_idx on propfolio.split_rule (landlord_id);
create index split_rule_utility_account_idx on propfolio.split_rule (utility_account_id);

-- -----------------------------------------------------------------------------
-- Bills (documents + extraction)
-- -----------------------------------------------------------------------------
create table propfolio.bill (
  id                 uuid primary key default gen_random_uuid(),
  landlord_id        uuid not null references propfolio.landlord (id) on delete cascade,
  utility_account_id uuid references propfolio.utility_account (id) on delete restrict, -- set once matched
  status             text not null default 'UPLOADED',
  original_filename  text not null,
  storage_path       text not null,
  content_type       text not null,
  file_size_bytes    bigint not null,
  file_sha256        text not null,
  provider_name      text,
  account_number     text,
  period_start       date,
  period_end         date,
  amount_due         numeric(12, 2),
  due_date           date,
  extraction_json    jsonb,
  confidence         numeric(4, 3),
  verified_at        timestamptz,
  created_at         timestamptz not null default now(),
  updated_at         timestamptz not null default now(),
  constraint bill_status_valid
    check (status in ('UPLOADED', 'EXTRACTED', 'READY', 'NEEDS_REVIEW', 'VERIFIED')),
  constraint bill_period_valid
    check (period_start is null or period_end is null or period_end >= period_start),
  constraint bill_confidence_valid check (confidence is null or (confidence >= 0 and confidence <= 1)),
  constraint bill_file_unique unique (landlord_id, file_sha256),
  -- A bill can only be VERIFIED once every field invoicing needs is present.
  constraint bill_verified_complete check (
    status <> 'VERIFIED' or (
      utility_account_id is not null and period_start is not null and period_end is not null
      and amount_due is not null and verified_at is not null
    )
  )
);
create index bill_landlord_status_idx on propfolio.bill (landlord_id, status);
create index bill_utility_account_idx on propfolio.bill (utility_account_id);

-- -----------------------------------------------------------------------------
-- Invoices
-- -----------------------------------------------------------------------------
create table propfolio.invoice (
  id               uuid primary key default gen_random_uuid(),
  landlord_id      uuid not null references propfolio.landlord (id) on delete cascade,
  invoice_number   text not null,              -- INV-YYYY-NNNN
  lease_id         uuid not null references propfolio.lease (id) on delete restrict,
  unit_id          uuid not null references propfolio.unit (id) on delete restrict,
  period_start     date not null,
  period_end       date not null,
  status           text not null default 'DRAFT',
  total_amount     numeric(12, 2) not null default 0,
  due_date         date,
  email_draft_id   text,
  email_drafted_at timestamptz,
  paid_at          timestamptz,
  voided_at        timestamptz,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now(),
  constraint invoice_number_unique unique (landlord_id, invoice_number),
  constraint invoice_status_valid
    check (status in ('DRAFT', 'EMAIL_DRAFTED', 'PARTIALLY_PAID', 'PAID', 'VOID')),
  constraint invoice_period_valid check (period_end >= period_start),
  constraint invoice_total_valid check (total_amount >= 0)
);
create index invoice_landlord_status_idx on propfolio.invoice (landlord_id, status);
create index invoice_lease_idx on propfolio.invoice (lease_id);
create index invoice_unit_period_idx on propfolio.invoice (unit_id, period_start);

-- One line per bill portion on an invoice. Share values are snapshotted so old
-- invoices stay correct even if split rules change later.
create table propfolio.invoice_line (
  id               uuid primary key default gen_random_uuid(),
  landlord_id      uuid not null references propfolio.landlord (id) on delete cascade,
  invoice_id       uuid not null references propfolio.invoice (id) on delete cascade,
  bill_id          uuid not null references propfolio.bill (id) on delete restrict,
  unit_id          uuid not null references propfolio.unit (id) on delete restrict,
  covered_start    date not null,
  covered_end      date not null,
  days_covered     integer not null,
  bill_period_days integer not null,
  share_type       text not null,
  share_value      numeric(12, 4) not null,
  amount           numeric(12, 2) not null,
  is_void          boolean not null default false, -- set true when the invoice is voided
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now(),
  constraint invoice_line_range_valid check (covered_end >= covered_start),
  constraint invoice_line_days_valid check (days_covered > 0 and bill_period_days > 0),
  constraint invoice_line_share_type_valid check (share_type in ('PERCENT', 'FLAT')),
  -- Double-billing protection: for the same bill and unit, active (non-void) lines
  -- may not cover overlapping days. Date ranges are inclusive on both ends.
  constraint invoice_line_no_double_billing exclude using gist (
    bill_id with =,
    unit_id with =,
    daterange(covered_start, covered_end, '[]') with &&
  ) where (not is_void)
);
create index invoice_line_landlord_idx on propfolio.invoice_line (landlord_id);
create index invoice_line_invoice_idx on propfolio.invoice_line (invoice_id);
create index invoice_line_bill_idx on propfolio.invoice_line (bill_id);

-- Invoice numbers: one running sequence per landlord per year (INV-YYYY-NNNN).
create table propfolio.invoice_number_sequence (
  id           uuid primary key default gen_random_uuid(),
  landlord_id  uuid not null references propfolio.landlord (id) on delete cascade,
  sequence_year integer not null,
  next_value   integer not null default 1,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now(),
  constraint invoice_number_sequence_unique unique (landlord_id, sequence_year),
  constraint invoice_number_sequence_value_valid check (next_value > 0)
);

-- -----------------------------------------------------------------------------
-- Payments
-- -----------------------------------------------------------------------------
create table propfolio.payment (
  id           uuid primary key default gen_random_uuid(),
  landlord_id  uuid not null references propfolio.landlord (id) on delete cascade,
  invoice_id   uuid not null references propfolio.invoice (id) on delete restrict,
  amount       numeric(12, 2) not null,
  paid_on      date not null,
  note         text,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now(),
  constraint payment_amount_valid check (amount > 0)
);
create index payment_landlord_idx on propfolio.payment (landlord_id);
create index payment_invoice_idx on propfolio.payment (invoice_id);

-- -----------------------------------------------------------------------------
-- updated_at triggers
-- -----------------------------------------------------------------------------
do $$
declare
  t text;
begin
  foreach t in array array[
    'landlord', 'property', 'unit', 'tenant', 'lease', 'utility_account', 'split_rule',
    'bill', 'invoice', 'invoice_line', 'invoice_number_sequence', 'payment'
  ]
  loop
    execute format(
      'create trigger %I before update on propfolio.%I
         for each row execute function propfolio.set_updated_at()',
      t || '_set_updated_at', t);
  end loop;
end;
$$;
