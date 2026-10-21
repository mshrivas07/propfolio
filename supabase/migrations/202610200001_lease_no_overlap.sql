-- =============================================================================
-- Propfolio Phase 2 — one lease per unit at a time
--
-- Backs up the service-layer check: leases on the same unit may not overlap.
-- Ranges are inclusive on both ends; a null end_date means the lease is ongoing.
-- Uses btree_gist, enabled in 202610010001_initial_schema.sql.
-- =============================================================================

alter table propfolio.lease
  add constraint lease_no_overlap exclude using gist (
    unit_id with =,
    daterange(start_date, end_date, '[]') with &&
  );
