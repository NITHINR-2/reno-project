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
