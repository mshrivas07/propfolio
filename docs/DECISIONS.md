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

## Ingestion

- MVP1: UI upload only (PDF / PNG / JPG).
- Files stored behind a `StorageService` interface; local folder path is configurable.

## Invoices

- Monthly auto-drafts plus custom from/to date ranges.
- Auto-drafts: when a bill becomes `VERIFIED`, create or recalculate the `DRAFT` invoice for each affected unit/month.
- Nothing goes to Gmail until the user clicks **Create Gmail draft**.
- Number format `INV-YYYY-NNNN`, one sequence across all properties.
- No tax in MVP1.
- Statuses: `DRAFT` → `EMAIL_DRAFTED` → `PARTIALLY_PAID` → `PAID`, plus `VOID`.
- An invoice is locked once `EMAIL_DRAFTED`; corrections mean void and reissue.
- Double-billing protection: each `invoice_line` records `bill_id` + covered date range; overlapping ranges for the same bill and unit are blocked unless the earlier invoice is `VOID`.

## Bills

- Statuses: `UPLOADED` → `EXTRACTED` → `READY` / `NEEDS_REVIEW` → `VERIFIED`.
- Only `VERIFIED` bills feed invoices.

## Sending

- Gmail API creates drafts in the user's account; the user reviews and sends.
- MVP1 uses a Google Cloud project in testing mode with the owner as test user.

## Payments

- Payments table supports partial payments (amount, date, optional note).
- `amount pending = invoice total − sum of payments`.
- **Mark as paid** becomes available when amount pending reaches 0.

## Open questions for later phases

- Phase 5: is a FLAT split amount per month (prorated by days) or per bill? Decide before building the billing engine.
- Phase 5: editing the dates of a lease that already has invoices is currently allowed; decide whether to lock it.

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
