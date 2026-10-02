# Propfolio (MVP1)

Helps small landlords split utility bills between units, turn them into tenant
invoices, and track what has been paid.

```
propfolio/
├── backend/               Spring Boot 4.1 API (Java 21, Maven)
├── frontend/              React + Vite single-page app
├── supabase/migrations/   SQL migrations for Supabase Postgres
└── docs/                  DECISIONS.md (what we decided) and PROGRESS.md (what's built)
```

## Prerequisites

- Java 21
- Maven 3.9+ (or generate a wrapper once: `cd backend && mvn -N wrapper:wrapper`)
- Node.js 20+
- Git

## Get the code

```bash
git clone https://github.com/mshrivas07/propfolio.git
cd propfolio
```

## Run locally

**Backend** (port 8080)

```bash
cd backend
cp .env.example .env        # fill in Supabase values
mvn spring-boot:run
```

Check it: <http://localhost:8080/api/health>

**Frontend** (port 5173)

```bash
cd frontend
cp .env.example .env
npm install
npm run dev
```

Open <http://localhost:5173>, sign in, and the dashboard shows "Signed in as …".

### Environment variables

- **Backend:** `backend/.env` is loaded automatically when the app starts from the `backend/` folder.
  In IntelliJ or VS Code, set the run configuration's working directory to `backend/`.
  Real environment variables still work and take priority over `.env`.
- **Frontend:** Vite reads `frontend/.env` automatically. Restart `npm run dev` after changing it.

### Database

Apply the SQL files in `supabase/migrations/` in the Supabase SQL Editor
(step-by-step in [supabase/migrations/README.md](supabase/migrations/README.md)).

## Build status

See [docs/PROGRESS.md](docs/PROGRESS.md) for what each phase has delivered and
[docs/DECISIONS.md](docs/DECISIONS.md) for design decisions and the MVP2 backlog.

Docker Compose setup arrives in Phase 8.
