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

## Proration and allocation

- Daily-rate allocation: `daily rate = bill amount ÷ days in bill period`.
- Day counting: calendar days, start and end dates **inclusive**.
- A tenant pays `share × daily rate × days their lease overlaps the invoice period`.
- The landlord absorbs vacant days.
- Each invoice line is rounded to the cent.
- Multi-month bills (e.g. quarterly water) are spread across monthly auto-drafts by day overlap.
- FLAT split amounts are **per bill**, prorated by the lease's days within the bill period
  (e.g. $50 internet, tenant present 15 of 30 days → $25). A multi-month bill's flat amount is
  spread across monthly drafts by day overlap, the same as PERCENT. (Decided in Phase 3.)
- Lease dates stay editable after invoicing. Existing invoices don't change; DRAFT invoices recalculate.
  (Decided in Phase 3.)

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

## Invoices

- Monthly auto-drafts plus custom from/to date ranges.
- Auto-drafts: when a bill becomes `VERIFIED`, create or recalculate the `DRAFT` invoice for each affected unit/month.
- Nothing goes to Gmail until the user clicks **Create Gmail draft**.
- Number format `INV-YYYY-NNNN`, one sequence across all properties.
- No tax in MVP1.
- Statuses: `DRAFT` → `EMAIL_DRAFTED` → `PARTIALLY_PAID` → `PAID`, plus `VOID`.
- An invoice is locked once `EMAIL_DRAFTED`; corrections mean void and reissue.
- Double-billing protection: each `invoice_line` records `bill_id` + covered date range; overlapping ranges for the same bill and unit are blocked unless the earlier invoice is `VOID`.

## Bills: reading and verification (decided in Phase 4)

- Statuses: `UPLOADED` → `READY` / `NEEDS_REVIEW` → `VERIFIED`. Status is re-evaluated after every reading
  and every edit. `EXTRACTED` is not used as a resting state. Only `VERIFIED` bills feed invoices.
- **The split amount is `current_charges`** (this period's charges incl. tax), not `amount_due`, which can
  include a previous balance or credits. Both are stored, plus `previous_balance`.
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
- A verified bill can't be edited or re-read. It can be un-verified only while it isn't on a non-void invoice line.

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
