# Database migrations

SQL migrations for the Supabase Postgres database, one file per change, named
`YYYYMMDDHHMM_description.sql` so they apply in order. Never edit a migration that
has already been applied; add a new file instead.

| File | What it does |
|---|---|
| `202610010001_initial_schema.sql` | Creates the `propfolio` schema and all MVP1 tables, constraints, indexes and `updated_at` triggers |

## How to apply a migration (Supabase SQL Editor)

1. Open your project at supabase.com and click **SQL Editor** in the left sidebar.
2. Click **New query**.
3. Open the migration file in your code editor, copy **all** of it, and paste it into the query window.
4. Click **Run**. You should see "Success. No rows returned".
5. Check the result: click **Table Editor**, open the schema dropdown at the top (it shows `public`
   by default) and choose `propfolio`. You should see 12 tables.
6. Note it in `docs/PROGRESS.md` (which migrations have been applied to your project).

If **Run** shows an error, copy the message and ask for help before running it again. If some
tables were already created, re-running fails on the ones that exist. Use "Starting over" below
to reset, then run the file again.

### Starting over (development only)

This deletes all Propfolio data:

```sql
drop schema propfolio cascade;
```

Then apply the migrations again from the first file.

## Why a separate `propfolio` schema

Supabase automatically publishes tables in the `public` schema through its Data API, which anyone
holding the (public) anon key can call. MVP1 has no Row Level Security, so app tables live in
`propfolio`, which the Data API does not expose, and the `anon` / `authenticated` roles have no
access to it. Only the Spring Boot backend reads and writes these tables.
