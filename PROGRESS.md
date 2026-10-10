# Progress

## Phase 0 — Environment check & monorepo scaffold (2026-10-10) ✅

### Toolchain (checked this session)

| Tool | Version |
|---|---|
| Java | 21.0.12 LTS (Oracle HotSpot, build 21.0.12+7-LTS-205). `JAVA_HOME` is unset, so the wrapper uses `java` on PATH. |
| Node.js | v24.14.1 |
| npm | 11.5.2 |
| Docker | 29.4.0 (Docker Desktop, daemon running) |
| Docker Compose | v5.1.1 |
| git | 2.51.0.windows.1 |
| Maven | 3.10.0 via the Maven Wrapper (no global `mvn` needed) |

Nothing was missing.

### Exact library versions

**Backend:** Spring Boot 4.1.1 · Spring Framework 7.0.9 · Spring Security 7.1.1 · Hibernate ORM 7.4.5.Final · Flyway 12.4.0 (+ flyway-database-postgresql) · PostgreSQL JDBC 42.7.13 · springdoc-openapi 3.1.1 · Tomcat 11.0.24 · Jackson 3.1.5 · JUnit Jupiter 6.0.3 · Mockito 5.23.0 · Testcontainers 2.0.5

**Frontend:** React 19.3.0 · TypeScript 6.0.3 · Vite 8.3.4 · Tailwind CSS 4.3.3 (`@tailwindcss/vite`) · React Router 8.4.0 · TanStack Query 5.104.1 · React Hook Form 7.89.0 · Zod 4.6.5 · @hookform/resolvers 5.9.1 · Axios 1.20.0 · Lucide React 1.55.0 · Vitest 5.0.3 · jsdom 29.1.1 · @testing-library/react 16.3.3 · @testing-library/jest-dom 7.0.1 · @testing-library/user-event 14.6.7 · oxlint 1.87.0

**Database:** postgres:16 image → PostgreSQL 16.15

### What was done

- Monorepo: `frontend/` (Vite React-TS), `backend/` (Spring Initializr, Boot 4.1.1, Maven Wrapper), `docker-compose.yml` (postgres:16 with healthcheck), `.gitignore`, and `.env.example` for root, backend and frontend.
- Backend: `application.yml` (shared + `dev` + `test` profiles, `ddl-auto: validate`, Flyway, ProblemDetail), Flyway `V1__baseline.sql`, `GET /api/health` (`health` feature: controller/service/dto), stateless `SecurityConfig` (health and docs public, everything else 401), and a `GlobalExceptionHandler` returning ProblemDetail.
- Backend tests: `FleetApplicationTests.contextLoads` and `HealthControllerTest` (health is public with DB UP; other endpoints return 401), both on Testcontainers postgres:16.
- Frontend: Tailwind v4, React Router, TanStack Query, RHF, Zod, Axios and Lucide installed; Vitest + RTL configured; `strict: true`; `@/` alias; `/api` proxy to `:8080`; `features/health` (Zod-validated API call, `useHealth` query hook, `HealthPage` with loading, error and success states); `components/{ui,layout,forms,tables}` placeholders.
- Docs: README skeleton (including the Finance GST flag), this file, `docs/DECISIONS.md`. CLAUDE.md stack line updated to Boot 4.x.

### Commands run and real results

| Command | Result |
|---|---|
| `docker compose up -d postgres` | Container `fleet-postgres` healthy; `postgres --version` → 16.15 |
| `./mvnw verify` (1st run) | **FAILED**: 3 errors, `FATAL: invalid value for parameter "TimeZone": "Asia/Calcutta"` |
| Fix: JVM forced to UTC (see DECISIONS.md) | — |
| `./mvnw verify` (2nd run) | **BUILD SUCCESS**: Tests run: 3, Failures: 0, Errors: 0 |
| `npm run test -- --run` | 1 file, **2 tests passed** |
| `npm run build` | **OK**: `tsc -b` clean; bundle 481 kB (151 kB gzip) |
| `npm run lint` | **OK**: oxlint, 0 findings |
| `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` | Flyway applied v1; started on :8080 in 4.4 s |
| `curl localhost:8080/api/health` | `200 {"status":"UP","database":"UP","version":"0.0.1-SNAPSHOT",...}` |
| `npm run dev` then `curl localhost:5173/api/health` (Vite proxy) | `200`, same JSON |
| `curl localhost:8080/api/fleet-requests` | `401` (secure by default) |
| `curl localhost:8080/v3/api-docs` | `200` |
| Headless Edge `--dump-dom http://localhost:5173/` | Rendered page contains "System health", "Backend API", two `UP` badges, and "0.0.1-SNAPSHOT" |

### Known issues / broken

- None blocking.
- The frontend bundle is a single 481 kB chunk. Route-level code splitting will come with real pages.
- `JAVA_HOME` is not set on this machine. That's harmless for the wrapper, but some IDEs may want it.

### Next

Waiting for the Phase 1 prompt. Nothing from later phases has been started.

---

## Phase 1 — Schema, JPA, auth, ProblemDetail, springdoc (2026-10-10) ✅

### New dependencies

`spring-boot-starter-security-oauth2-resource-server` 4.1.1 → Spring Security OAuth2 Resource Server / JOSE 7.1.1, Nimbus JOSE+JWT 10.9.1. No other additions (no Lombok, no jjwt).

### What was done

- **Flyway V2–V6** (V1 unchanged): 20 business tables (the derived list approved by the project owner — see DECISIONS.md) plus `tax_rates` and `refresh_tokens` = 22 tables.
  - Constraints: PKs, FKs (all indexed), unique business numbers, NOT NULL, enum CHECKs, money/quantity CHECKs, `created_at`/`updated_at`/`version` on mutable tables.
  - `ux_quotations_one_issued` partial unique index (one ISSUED quotation per request).
  - `CHECK (quantity_available >= 0)` on stock; case/space-insensitive unique `transaction_reference`; non-overlapping effective-dated `tax_rates` (`EXCLUDE USING gist`, `btree_gist`).
  - Append-only triggers on `audit_logs` and `workflow_history`.
  - V6 seeds 5 roles, 21 permissions and the role→permission mapping (all environments).
- **JPA:** 22 entities + repositories, package-by-feature (`user`, `auth`, `dealer`, `catalog`, `production`, `inventory`, `fleetrequest`, `quotation`, `payment`, `dispatch`, `workflow`, `audit`), shared `BaseEntity`, `ddl-auto=validate` passes.
- **Auth:**
  - `POST /api/auth/login`, `POST /api/auth/refresh`, `POST /api/auth/logout`, `GET /api/auth/me`.
  - HS256 access JWT (15 min, memory-only on the client).
  - Rotating refresh token in a `fleet_rt` cookie: `HttpOnly; Secure; SameSite=Strict; Path=/api/auth`. It is stored as SHA-256, rotation locks the row, and reusing a rotated token revokes the family.
  - `X-Requested-With` is required on refresh/logout (403 ProblemDetail otherwise).
  - Authorities (`ROLE_*` + permission codes) are reloaded from the DB on every request.
  - Method security via `@PreAuthorize`.
  - Auth events are audited (LOGIN_SUCCESS / LOGIN_FAILED / LOGOUT / REFRESH_TOKEN_REUSE_DETECTED).
  - `dev`-profile seeding of one user per role (`admin`, `supervisor`, `marketing.manager`, `marketing.head`, `finance`) from `DEV_*_PASSWORD`.
- **Fleet stub:** `GET /api/fleet-requests` (paginated, `status` filter, `FLEET_REQUEST_READ`), returning a `PageResponse` DTO.
- **Errors:**
  - `GlobalExceptionHandler` covers ApiException (incl. 404/409 subclasses), 401, 403, optimistic lock 409, data integrity 409, bad sort 400, validation 400 with an `errors` map, and a catch-all 500.
  - Security-filter 401/403 are routed through the same handler, so every error is `application/problem+json`.
- **springdoc:** `bearerAuth` JWT scheme applied globally; auth endpoints opt out.
- `JWT_SECRET` added to `backend/.env.example`; a generated value was appended to the local, git-ignored `backend/.env`.

### Commands run and real results

| Command | Result |
|---|---|
| Migrations V2–V6 applied with `psql -v ON_ERROR_STOP=1` to a scratch DB in `fleet-postgres` | All applied; 22 tables; role permission counts ADMIN 3 / FIN 6 / MH 5 / MM 4 / SUP 21. Scratch DB dropped. |
| `./mvnw -q verify` (entities only, before auth) | exit 0 — Hibernate `validate` accepted every mapping |
| `./mvnw verify` (1st full run) | **FAILED**: 37 tests, 2 failures — logout 500 (`LazyInitializationException` on role) and audit-trigger SQLSTATE 42501 being translated as "bad SQL grammar" |
| Fixes: logout loads user via join-fetch; trigger uses `restrict_violation` (23001) | — |
| `./mvnw verify` (2nd run) | **BUILD SUCCESS** — Tests run: 37, Failures: 0, Errors: 0 (AuthControllerTest 16, FleetRequestAuthorizationTest 8, MigrationSchemaTest 8, OpenApiTest 2, HealthControllerTest 2, FleetApplicationTests 1) |
| `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` (compose DB at V1) | Flyway applied V2–V6 ("Successfully applied 5 migrations … now at version v6"); 5 dev users seeded; started in 17.4 s |
| curl login `marketing.manager` → `/api/auth/me` | 200; role MARKETING_MANAGER with its 4 permissions |
| curl `/api/fleet-requests` as MARKETING_MANAGER / ADMIN / no token | 200 empty page / **403** ProblemDetail / **401** ProblemDetail |
| curl refresh without / with `X-Requested-With` | 403 / 200 with `Set-Cookie: fleet_rt=…; Path=/api/auth; Max-Age=604800; Secure; HttpOnly; SameSite=Strict` |
| curl login with bad password | 401 `Invalid username or password.` |
| curl `/swagger-ui.html` → `/swagger-ui/index.html`; `/v3/api-docs` | 302 → 200 "Swagger UI"; docs list `/api/auth/{login,logout,me,refresh}`, `/api/fleet-requests`, `/api/health` |

Frontend not touched this phase (no frontend commands run).

### Known issues / broken

- None blocking.
- `spring-boot:run` via the wrapper leaves the forked JVM running if only the Maven process is killed; stop it with Ctrl+C in a terminal.
- Expired/revoked `refresh_tokens` rows are never purged yet (cleanup job is a later-phase item).
- No login rate limiting / lockout yet (only the public verification page is required to be rate-limited, in Phase 6).
- Business seed data (FLT-2026-0891, dealers, models, plants, inventory, the 18% Duster tax rate) is **not** seeded yet — only users. It belongs with the phase that seeds business data.

### Next

Waiting for the Phase 2 prompt. Nothing from later phases has been started.
