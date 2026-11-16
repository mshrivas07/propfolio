# Decisions

Recorded in Phase 0. Change a decision here first, then in code.

## Stack

| Area | Decision |
|---|---|
| Frontend | React + Vite + React Router + Tailwind CSS + Zustand. Plain SPA; all features go through the REST API so a mobile client can be added later. |
| Backend | Spring Boot 4.1.x (Spring Framework 7, Spring Security 7, Jackson 3), Java 21, Maven. Modular monolith, one deployable. |
| Backend packages | `security`, `rentals`, `documents`, `extraction`, `billing`, `notifications`, `payments`, `common` |
| Database | Supabase Postgres |
| Auth | Supabase Auth issues the JWT; Spring Boot validates it as an OAuth2 resource server against the Supabase JWKS (ES256/RS256), checking expiry, issuer (`<SUPABASE_URL>/auth/v1`) and audience (`authenticated`). No RLS in MVP1. |
| AI extraction | OpenAI API, called only from the backend (key never reaches the browser) |
| Environment | Local only for MVP1. Docker Compose in Phase 8. |

## Multi-landlord

- Every table carries `landlord_id` (the Supabase auth user id).
- Every query is scoped to the current user in the service layer.
- Supabase email signup creates new landlords.

## Database (decided in Phase 1)

- All app tables live in the `propfolio` schema, which Supabase's Data API does not expose.
  `anon` and `authenticated` have no access. Only the backend touches the tables.
- Supabase migrations (`supabase/migrations`) own the schema. Hibernate runs with `ddl-auto: validate`.
- Statuses and types are `text` + `CHECK` constraints, not Postgres enums, so values are easy to add.
- Entities reference other tables by id (UUID fields), not JPA relationships.
- `landlord.id` = Supabase auth user id; the row is created on the user's first API call.
- Double-billing protection is a Postgres `EXCLUDE` constraint on `invoice_line`
  (same bill + unit, overlapping inclusive date range, not void). Uses the `btree_gist` extension.
- A bill can only become `VERIFIED` when utility account, period, amount and verified_at are set (CHECK).
- Tests use H2 in PostgreSQL mode with the schema generated from entities. Constraint behaviour is
  verified against real Postgres when migrations change. Testcontainers can replace H2 later.
- Local config: `backend/.env` is loaded by `spring.config.import: optional:file:.env[.properties]`.

## Rentals and split rules

- A property has utility accounts and units; a lease links a tenant to a unit with start/end dates.
- Split rules: **one per unit per utility account**; type `PERCENT` or `FLAT`.
- Flat amounts are prorated the same way as percentages.
- Direction is landlord → tenant only. (A landlord who is reimbursed by their own landlord models that person as a tenant.)

## Proration and charges (decided in Phase 5A; replaces the earlier monthly / spread-across-months rules)

- **Bill by bill.** A bill is never split across calendar months. Each lease that overlaps a verified bill's
  period gets one **charge** (`invoice_line`) for that bill.
- `PERCENT`: `split amount × percent ÷ 100 × days covered ÷ bill period days`.
- `FLAT`: `flat amount × days covered ÷ bill period days` — a flat amount is **per bill**
  (e.g. $50 internet, tenant present 15 of 30 days → $25).
- Days covered = days the lease overlaps the bill period. Calendar days, start and end **inclusive**.
- Rounded **once**, to the cent, half up. Vacant days aren't charged; the landlord absorbs them.
- Two tenants in one bill period (move-out/move-in) each pay for their own days.
- **Equal payment plan** (`utility_account.equal_payment_plan`): the split amount is the bill's
  `amount_due` (the plan payment); otherwise it's `current_charges`. Fixed as `bill.split_amount` when the
  bill is verified, so later flag changes affect only bills verified afterwards.
- Lease dates stay editable after invoicing. Locked (non-OPEN) invoices never change; the OPEN invoice
  recalculates.

## Rental setup (decided in Phase 2)

- One active lease per unit at a time: leases on the same unit never overlap (inclusive dates;
  a null end date means ongoing). Enforced in `LeaseService` and by the `lease_no_overlap`
  exclusion constraint. Back-to-back leases (one ends Jun 30, next starts Jul 1) are fine.
- Roommates sharing a unit are one tenant record with several email addresses.
- Tenant emails are trimmed, lower-cased and de-duplicated (first-entered order kept); at least one, at most 10.
- Split rules: PERCENT values on one utility account add up to at most 100 (the landlord pays the rest).
  FLAT rules may sit alongside PERCENT rules on the same account and are not counted in that total.
  FLAT amounts have at most 2 decimals; percentages at most 4.
- A split rule's unit and utility account must be on the same property.
- Property and unit names are unique (case-insensitive) per landlord / per property.
- Deleting is blocked (409) while other records depend on the row:
  property → units or utility accounts; unit → leases or invoices; tenant → leases;
  lease → invoices (end it instead); utility account → bills.
  Deleting a unit or utility account deletes its split rules.
- "Today" for lease status (UPCOMING / CURRENT / ENDED) uses `app.time-zone` (default America/Toronto).

## API conventions (decided in Phase 2)

- REST under `/api`; request/response bodies are Java records; entities never leave the service layer.
- Every query is scoped to the current landlord. Another landlord's id returns **404**, never 403.
- Errors are RFC 9457 ProblemDetail (`application/problem+json`):
  - 400 `Invalid request` with `errors: [{ field, message }]` (list items use `emails[1]` style names)
  - 404 `Not found`, 409 `Conflict` with a readable `detail`
  - Database constraint violations that slip past service checks become a generic 409.
- `PUT /api/split-rules` creates or replaces the rule for a unit + utility account pair.
- `GET /api/properties/{id}` returns the whole property setup (units with leases, utility accounts,
  split rules, per-account percent totals) so the detail screen needs one call.

## Ingestion (decided in Phase 3)

- MVP1: UI upload only (PDF / PNG / JPG), one file per request; the UI uploads several one after another.
- File type is decided by the file's signature (first bytes), never by name or browser Content-Type.
  The stored `content_type` is the detected one.
- Limit 10 MB per file (checked in `BillService`; the container multipart limit is 12 MB so users see the clear message).
- Duplicates: same SHA-256 for the same landlord → 409 with `existingBillId` in the ProblemDetail.
  Different landlords may upload the same file.
- Optional utility account at upload; it must belong to the landlord (400 on `utilityAccountId` otherwise).
- Storage behind `StorageService` (store / open / delete). MVP1 uses `LocalStorageService` under
  `app.storage.local-root` (default `./storage`, relative to `backend/`).
- Storage key: `<landlordId>/<yyyy>/<MM>/<billId>.<ext>` (upload month in `app.time-zone`); never the original filename.
  Keys are validated (safe characters only, no `.`/`..` segments, must stay inside the root).
- Files are written to a temp file and moved into place, so half-written files never appear.
- If the database transaction doesn't commit after the file was stored, the file is deleted
  (transaction synchronization). A deleted bill's file is removed after commit.
- The original filename is kept for display only (last path part, control characters removed, max 200 chars).
- `GET /api/bills/{id}/file` streams the file inline with `Cache-Control: no-store, private`.
  The UI fetches it with the token and shows it from a blob URL.
- A bill can't be deleted once `VERIFIED` or once it appears on an invoice line.

## Invoices (decided in Phase 5A; replaces monthly auto-drafts and custom date ranges)

- Each lease has **one `OPEN` invoice** that collects its charges as bills are verified. It's created with the
  first charge and **kept when it becomes empty**, so invoice numbers have no gaps.
- The OPEN invoice is **rebuilt, not patched**, whenever something affecting charges changes: a bill is verified
  or un-verified, a split rule is saved or deleted, a lease is created, edited or deleted.
  Expected charges = the lease's share of every verified bill (on its unit's split-rule accounts) overlapping
  the lease, minus bills already charged to that lease on a locked (non-OPEN, non-void) invoice. Rebuilding twice changes nothing.
- Invoice period = earliest to latest covered date of its charges (empty while it has none).
- Due date = creation date + 15 days; editable while OPEN.
- Un-verifying a bill is allowed while its charges are only on OPEN invoices (they're removed), and blocked
  once a charge is on a locked invoice.
- A lease whose only invoice is OPEN can be deleted (the OPEN invoice goes with it); once invoiced, it can't.
- Number format `INV-YYYY-NNNN`, one sequence per landlord per year across all properties. The sequence row is
  locked while a number is taken, so concurrent requests never share a number.
- Nothing goes to Gmail until the user clicks **Create Gmail draft**.
- No tax in MVP1.
- Statuses in 5A: `OPEN`, plus the old `EMAIL_DRAFTED`, `PARTIALLY_PAID`, `PAID`, `VOID` until Phase 5B reworks
  them (OPEN → INVOICE_CREATED → EMAIL_DRAFTED → SENT → PARTIALLY_PAID → PAID → ARCHIVED, plus CANCELLED).
- Double-billing protection: each `invoice_line` records `bill_id` + covered date range; overlapping ranges for
  the same bill and unit are blocked unless the line is void.

## Bills: reading and verification (decided in Phase 4)

- Statuses: `UPLOADED` → `READY` / `NEEDS_REVIEW` → `VERIFIED`. Status is re-evaluated after every reading
  and every edit. `EXTRACTED` is not used as a resting state. Only `VERIFIED` bills feed invoices.
- **The split amount is `current_charges`** (this period's charges incl. tax), not `amount_due`, which can
  include a previous balance or credits. Both are stored, plus `previous_balance`. Exception (Phase 5A):
  equal payment plan accounts split `amount_due`, the plan payment.
- Reading runs automatically in the background after the upload commits (`app.bills.auto-extract`),
  two at a time, on a small thread pool. "Read again" / "Read bill" re-runs it and replaces the read fields.
- Reading state is derived from `extraction_requested_at`, `extracted_at` and `extraction_error`:
  NOT_STARTED, RUNNING, DONE or FAILED. A reading unfinished after 10 minutes shows as failed.
  A reading that finishes after a newer request (or after the bill was verified or deleted) is discarded.
- A failed reading leaves the bill `UPLOADED` with a readable reason; the landlord can retry or type the details in.
- OpenAI: Responses API over Spring `RestClient` (no SDK). PDFs go as `input_file`, images as `input_image`
  (base64 data URLs). Strict structured output (`resources/extraction/utility-bill-schema.json`),
  instructions in `resources/extraction/instructions.md`, `store: false`. Default model `gpt-6-luna`
  (configurable `OPENAI_MODEL`). Bill contents and the API key are never logged.
- Review rules (`BillChecks`): period present, ends on/after start, at most 120 days; current charges present
  and > 0; amount due within $0.05 of current + previous; account number matches the linked account
  (spaces/dashes ignored); bill type matches the account type; provider names share a distinctive word;
  no other bill on the same account covers overlapping days; every field the reader was less than
  85% sure about is flagged until the landlord changes it.
- Combined confidence = average reader confidence over provider, account number, period start/end and
  current charges (typed or changed values count as 1.0, missing as 0), minus 15% per failed rule.
  READY needs no issues and combined confidence ≥ `app.bills.ready-threshold` (0.85).
- Matching: with no account chosen, a reading links the bill to the landlord's only utility account with the
  same normalized account number. An account chosen at upload is never changed by a reading;
  a different number on the bill makes it `NEEDS_REVIEW`.
- Verify needs utility account, billing period (end ≥ start) and current charges > 0 (all missing fields are
  reported together), and is blocked when another verified bill on the same account overlaps those days.
- A verified bill can't be edited or re-read. It can be un-verified only while its charges are on OPEN invoices only.
- Plan accounts (Phase 5A): the plan payment (amount due) replaces current charges in the "present and > 0" rule,
  in the combined confidence and at verify; the "amount due = current + previous" check is skipped. The reader
  reports `on_payment_plan`; a disagreement with the account's flag is a review issue.

## Sending

- Gmail API creates drafts in the user's account; the user reviews and sends.
- MVP1 uses a Google Cloud project in testing mode with the owner as test user.

## Payments

- Payments table supports partial payments (amount, date, optional note).
- `amount pending = invoice total − sum of payments`.
- **Mark as paid** becomes available when amount pending reaches 0.

## MVP2 backlog

- Inbox-folder / email ingestion (daily `@Scheduled` job)
- HST / tax on recharged utilities
- Cloud storage: Supabase Storage first, then Cloudflare R2
- SaaS onboarding (plans, billing, admin)
- Google app verification for Gmail scopes (required before other landlords can use drafts)
- Row Level Security policies as defense in depth

## Spring Boot version (decided 2026-09-30)

- Moved from 3.5.x to **4.1.x** before any business code was written.
- Why: Spring Boot 3.5 open-source support ended 2026-06-30, so it no longer gets free security patches.
  4.0.x OSS support ends 2026-12-31; 4.1.x is supported until mid-2027.
- Boot 4 conventions to follow from here on:
  - Starters are per module: `spring-boot-starter-webmvc` (not `-web`), and matching `-test` starters
    (e.g. `spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`).
  - Test-slice annotations live in module packages, e.g.
    `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` / `WebMvcTest`.
  - JSON uses Jackson 3 (`tools.jackson.*` packages) when we need to touch it directly.
  - Spring Security 7: lambda DSL only.
