# Progress

Each phase reads this file first and updates it last.

## Phase 0 — Scaffold ✅

**Goal:** empty but runnable monorepo, no business logic.

### Files created

Root
- `README.md`
- `.gitignore`

Docs
- `docs/DECISIONS.md`
- `docs/PROGRESS.md`

Database
- `supabase/migrations/README.md`

Backend
- `backend/pom.xml`
- `backend/.env.example`
- `backend/src/main/java/com/propfolio/PropfolioApplication.java`
- `backend/src/main/java/com/propfolio/{security,rentals,documents,extraction,billing,notifications,payments,common}/package-info.java` (8 files)
- `backend/src/main/java/com/propfolio/common/web/HealthController.java`
- `backend/src/main/java/com/propfolio/common/config/CorsProperties.java`
- `backend/src/main/java/com/propfolio/common/config/WebConfig.java`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-local.yml`
- `backend/src/test/java/com/propfolio/PropfolioApplicationTests.java`
- `backend/src/test/java/com/propfolio/common/web/HealthControllerTest.java`

Frontend
- `frontend/package.json`, `frontend/package-lock.json`
- `frontend/vite.config.js`
- `frontend/index.html`
- `frontend/.env.example`
- `frontend/public/favicon.svg`
- `frontend/src/main.jsx`
- `frontend/src/App.jsx`
- `frontend/src/index.css`
- `frontend/src/layouts/AppLayout.jsx`
- `frontend/src/pages/DashboardPage.jsx`
- `frontend/src/pages/LoginPage.jsx`
- `frontend/src/pages/ComingSoonPage.jsx`
- `frontend/src/pages/NotFoundPage.jsx`
- `frontend/src/components/ApiStatus.jsx`
- `frontend/src/lib/config.js`
- `frontend/src/lib/api.js`
- `frontend/src/stores/authStore.js`

### What works

- `GET /api/health` returns `{ status, service, timestamp }` (no auth).
- CORS allows `http://localhost:5173` (configurable via `APP_CORS_ALLOWED_ORIGINS`).
- Frontend routes: `/login` (shell only), `/` dashboard with live API status, placeholders for Properties, Bills and Invoices.
- Frontend production build verified (`npm run build`).

### Decisions made in this phase

- Project name: **Propfolio** (repo `github.com/mshrivas07/propfolio`). Base Java package `com.propfolio`, main class `PropfolioApplication`, API service name `propfolio-api`.
- Frontend uses React Router v7 (`react-router` package) and Tailwind CSS v4 via the Vite plugin.
- UI tokens (colours, Public Sans font) live in `frontend/src/index.css` under `@theme`.
- Backend starts with only `webmvc` + `validation` so it runs without a database or secrets.

### Deferred

- JPA, Postgres driver, Spring Security / OAuth2 resource server → Phase 1
- Supabase JS client, real sign-in, route guard → Phase 1
- Maven wrapper (`mvn -N wrapper:wrapper`) → generate locally, then commit `.mvn/` and `mvnw*`
- Docker Compose → Phase 8
- Backend not compiled in the scaffolding environment; first `mvn test` run is the check

### Open items before Phase 1

- Create the Supabase project and collect URL, anon key, JWKS URI and DB connection string.

## Phase 0.1 — Spring Boot 4 upgrade ✅

Branch: `release/1.0.1`

### Files modified

- `backend/pom.xml` — parent 3.5.6 → 4.1.1; `spring-boot-starter-web` → `spring-boot-starter-webmvc`; added `spring-boot-starter-webmvc-test`
- `backend/src/test/java/com/propfolio/common/web/HealthControllerTest.java` — `AutoConfigureMockMvc` import moved to its Boot 4 package
- `README.md` — stack line
- `docs/DECISIONS.md` — version decision and Boot 4 conventions
- `docs/PROGRESS.md` — this entry

### Files created

- None

### Unchanged (verified compatible)

- `PropfolioApplication`, `HealthController`, `CorsProperties`, `WebConfig`, `PropfolioApplicationTests`, both YAML files
- Frontend (no backend-version dependency)

### Verification

- Run `mvn test` locally; both tests should pass. (Not compiled in the scaffolding environment: no Maven Central access there.)
