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

---

## Phase 2 — Domain services, WorkflowService, marketing/executive endpoints, stock allocation, dev seed (2026-10-11) ✅

### New dependencies

None.

### What was done

- **Flyway V7:**
  - `fleet_requests.stock_mapped_at/by`, with a both-or-neither CHECK
  - `CHECK (production_required = quantity - allocated_qty)`
  - `fleet_request_number_seq` (starts at 1000)
- **Services, DTOs, validation, pagination and filtering** (package-by-feature, hand-written mappers, `PageResponse`, JPA `Specification` filters):
  - **Dealers:** `GET /api/dealers` (`q`, `regionId`, `status`), `GET /{id}`, `POST`, `PATCH` (onboarding/activation, audited). Write needs `ADMIN_DEALERS_MANAGE`; read needs it or `FLEET_REQUEST_READ`.
  - **Vehicle catalogue:** `GET /api/vehicle-models` (`q`, `category`, `active`, variants included) and `GET /{id}`. Read-only.
  - **Inventory:**
    - `GET /api/inventory/locations` (`type`, `regionId`, `plantId`, `active`)
    - `GET /api/inventory/stock` (`variantId`, `modelId`, `locationId`, `locationType`, `availableOnly`, `activeLocationsOnly`)
    - `GET /api/fleet-requests/{id}/stock-options`
    - `PUT /api/fleet-requests/{id}/stock-allocations` (`STOCK_MAP`)
  - **Fleet requests:**
    - `GET /api/fleet-requests` (multi-`status`, `dealerId`, `regionId`, `variantId`, `modelId`, `plantId`, `q`, `createdFrom/To`)
    - `GET /{id}`: detail with allocations, eligible stock, `depositReceived` and `allowedActions`
    - `GET /{id}/history`
    - `POST` and `PATCH` (PENDING_MARKETING_REVIEW only, `version` required)
- **Workflow:**
  - `WorkflowTransitions` is the explicit table of every CLAUDE.md row: (from, action, to, roles, guard, reason required), with SUPERVISOR on every human row and an automatic QUOTATION_ISSUED → PAYMENT_PENDING row.
  - `WorkflowService.transition` locks the request row, then checks in order: role (403), state (409), version (409), reason (400) and guard (409).
  - Each transition writes one `workflow_history` row and one `audit_logs` row (`WORKFLOW_<ACTION>`, before/after JSON) in the same transaction.
  - CANCEL releases allocations. It is service-level only for now.
- **Endpoints:**
  - Marketing: `POST /{id}/submit-for-qualification`, `/forward-to-marketing-head`, `/resubmit`
  - Executive: `POST /{id}/approve`, `/reject` (reason required), `/forward-to-finance`
- **Stock allocation:**
  - The request row and every touched stock row are locked with `SELECT … FOR UPDATE` in ascending id order. Stock ids are read before locking, so no stale managed entity is used.
  - Old allocations are released and flushed before new ones are inserted.
  - Production is recomputed and a `STOCK_MAPPED` history event plus an audit row are written.
  - `PessimisticLockingFailureException` → 409.
- **Dev seed (`DevBusinessSeeder`, `@Order(2)` after `DevUserSeeder`):**
  - 3 regions, 2 plants, 3 dealers (2 ACTIVE)
  - Duster, Kiger, Triber and Kwid variants
  - 3 locations with stock; Duster RXZ Petrol has 30 units at Chennai Yard
  - SUV GST 18% (flagged for Finance)
  - FLT-2026-0891, unallocated, with an APPROVED ₹10,00,000 deposit receipt
- **Docs:** a Phase 2 section in DECISIONS.md, and the dev data plus the Swagger walkthrough in the README.

### Commands run and real results

| Command | Result |
|---|---|
| `./mvnw -q -o compile` (after main code) | exit 0 |
| `./mvnw -o verify` (existing tests only, with V7) | **BUILD SUCCESS**: 37 tests, 0 failures. `MigrationSchemaTest` fixture updated for the new production CHECK and V7. |
| `./mvnw -o verify` (with new tests, 1st run) | **FAILED**: 1243 tests, 16 failures, all test-side. Postgres `jsonb` normalises whitespace, so string `contains` checks on audit JSON failed. `jsonPath(...).value(List.of(...))` can't coerce to an immutable list. A dealer list test shared a region with another test. A 403 check sent an invalid body, and validation runs before method security, giving 400. |
| Fixes: parse audit JSON with JsonPath, use Hamcrest `contains`, give the dealer test its own region, send a valid body for the 403 check (behaviour logged in DECISIONS.md) | — |
| `./mvnw -o test -Dtest=DealerApiTest,FleetRequestCrudApiTest,Flt0891WalkthroughTest,FleetRequestWorkflowApiTest` | **BUILD SUCCESS**: 201 tests, 0 failures |
| End-to-end check on scratch DB `fleet_p2check`: `java -jar target/fleet-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev --server.port=18080` | Flyway "Successfully applied 7 migrations … now at version v7". 5 users seeded, then "Dev seed: created FLT-2026-0891 (PENDING_MARKETING_REVIEW)". |
| curl walkthrough (the same endpoints Swagger exposes) | Detail: Chennai North / Duster RXZ Petrol / qty 50 / eligible stock 30 / deposit required 5,000,000 / received 1,000,000 / Pune Plant / 6 weeks. Map 30 → allocated 30, production 20, `allowedActions` [QUALIFY]. MM approve → **403**. Forward before qualify → **409**. Qualify → MARKETING_QUALIFIED; forward → PENDING_EXECUTIVE_APPROVAL; MH approve → EXECUTIVE_APPROVED; forward-to-finance → **PENDING_FINANCE_CLEARANCE**. History has 6 rows. `audit_logs` has DEV_SEED, STOCK_MAPPED and 4 WORKFLOW_* rows with correct before→after status. Stock row: available 0, allocated 30. `/v3/api-docs` lists all 17 new or updated paths. |
| Scratch app stopped, `drop database fleet_p2check` | Done. The dev database `fleet` was not touched. |
| `./mvnw -o verify` (final) | **BUILD SUCCESS**: **1243 tests, 0 failures, 0 errors** (1:14 min) |

**Test breakdown:**
- WorkflowTransitionsTest: 985, every status × action × role plus table, guard and reason checks
- FleetRequestWorkflowApiTest: 191, each endpoint × 5 roles (200 or 403) and every wrong source state (409), plus guard, reason, version, 404 and reject/resubmit loop
- AuthControllerTest: 16
- FleetRequestAuthorizationTest: 8
- MigrationSchemaTest: 8
- ProductionCalculatorTest: 8
- FleetRequestCrudApiTest: 6
- DealerApiTest: 3
- CatalogInventoryApiTest: 3
- StockMappingApiTest: 3
- TransactionRollbackTest: 3, covering a failing second line, a failed remap keeping the original allocation, and an audit failure rolling back the transition
- StockAllocationConcurrencyTest: 2, with 2 threads racing for the last unit (exactly 1 wins) and 8 threads racing for 5 units (exactly 5 win; losers get a clean 409)
- OpenApiTest: 2
- HealthControllerTest: 2
- FleetApplicationTests: 1
- WorkflowServiceTest: 1, cancel needs a reason, is SUPERVISOR-only and releases stock
- Flt0891WalkthroughTest: 1, real dev seeders, the full Swagger path

Frontend not touched this phase (no frontend commands run).

### Known issues / broken

- None blocking.
- The first `spring-boot:run` against the local `fleet` database will apply V7 and seed the business data. That database has not been started with this code yet.
- Body validation (400) runs before `@PreAuthorize` (403). An unauthorised caller sending a malformed body gets 400. Logged in DECISIONS.md.
- Finance-row guards (issued quotation, approved receipts ≥ required) are not implemented; no endpoint reaches those rows yet.
- CANCEL and other Supervisor actions have no HTTP endpoints yet.
- No regions/plants list endpoints yet; the frontend create form will need them.
- Business audit rows don't record the client IP.

### Next

Waiting for the Phase 3 prompt. Nothing from later phases has been started.

---

## Phase 3A — Quotations, payment receipts, funds verification, clearance (2026-10-11) ✅

### New dependencies

None.

### What was done

- **Flyway V8:** `quotation_number_seq` and `receipt_number_seq`.
- **Workflow:** the finance guards are now live through `WorkflowFacts`:
  - ISSUE_QUOTATION needs an ISSUED quotation.
  - VERIFY_FUNDS and GRANT_CLEARANCE need approved deposits ≥ the required deposit.
  - `WorkflowService.verifyFundsIfCovered` performs PAYMENT_PENDING → FUNDS_VERIFIED when covered.
- **`QuotationCalculator`:** pure BigDecimal rules. Per line, `base = qty × unitPrice` and `gst = base × rate`, each HALF_UP to 2 dp, then summed. `total = base + gst`, `shortfall = max(0, required − eligibleDeposits)`, with a NUMERIC(15,2) overflow check.
- **Quotation endpoints:**
  - `POST /api/fleet-requests/{id}/quotations/calculate` (`QUOTATION_ISSUE`) creates a DRAFT with the full breakdown. Earlier drafts become CANCELLED. Allowed only in PENDING_FINANCE_CLEARANCE; 409 if there's no GST rate for the category.
  - `POST /api/quotations/{id}/issue` (`QUOTATION_ISSUE`) moves DRAFT → ISSUED (issue date, valid until +30 days). The request then goes QUOTATION_ISSUED → PAYMENT_PENDING through `WorkflowService`, with the second step taken automatically by the system. A stale draft gets 409. `paymentStatus` comes only from approved receipts.
  - `GET /api/fleet-requests/{id}/quotations` and `GET /api/quotations/{id}` (`QUOTATION_READ`).
- **Receipt endpoints:**
  - `POST /api/fleet-requests/{id}/payment-receipts` (`PAYMENT_RECORD`). The transaction reference must be unique ignoring case and spaces (409). The received date can't be in the future (IST). Finance stages only.
  - `POST /api/payment-receipts/{id}/approve` (`PAYMENT_APPROVE`). It locks the request row and then the receipt row, and moves RECORDED → APPROVED exactly once; a repeat is 409. It refreshes the issued quotation's payment status, and runs VERIFY_FUNDS automatically when approved deposits cover the requirement.
  - `GET /api/fleet-requests/{id}/payment-receipts` and `GET /api/payment-receipts/{id}` (`PAYMENT_RECORD` or `DEPOSIT_REVIEW`).
- **Clearance:** `POST /api/fleet-requests/{id}/grant-clearance` (`FINANCE_CLEARANCE`) moves FUNDS_VERIFIED → FINANCIALLY_CLEARED.
- **Audit:** every action writes audit rows with before/after JSON, plus `workflow_history` events.
- **Config:** `fleet.quotation.validity-days` (30) and `fleet.quotation.default-terms` (placeholder).

### Commands run and real results

| Command | Result |
|---|---|
| `./mvnw -q -o test-compile` | exit 0 |
| `./mvnw -o test -Dtest=QuotationCalculatorTest,FinanceApiTest,WorkflowTransitionsTest,MigrationSchemaTest` | **BUILD SUCCESS**: 1024 tests, 0 failures (calculator 19, finance API 11, transitions 986, schema 8) |
| `./mvnw -q -o verify` | **exit 0**. Surefire totals: **1274 tests, 0 failures, 0 errors**. |

One gap was found and fixed while writing the tests, before any run. Deposits approved *before* issue didn't update the issued quotation's `paymentStatus`, because no ISSUED quotation existed at approval time. Issue now derives it from approved receipts.

**New tests:**

- **`QuotationCalculatorTest` (19):**
  - FLT-2026-0891 figures: base 6,00,00,000.00, GST 1,08,00,000.00, total 7,08,00,000.00, shortfall 40,00,000.00
  - HALF_UP at .005 (0.045 → 0.05), and sub-paisa unit prices
  - per-line rounding before summing (2 × 0.0054 → 0.02, not 0.01)
  - zero-rated and free lines, a near-max NUMERIC(15,2) total, overflow rejected
  - shortfall never negative (overpaid, equal, 1 paisa short)
  - invalid inputs rejected
- **`FinanceApiTest` (11):**
  - The draft breakdown, and recalculation replacing the draft.
  - Issuing is not payment. After issue there are no receipts, the quotation is UNPAID, the request stays PAYMENT_PENDING and clearance gets 409. History reads QUOTATION_CALCULATED → ISSUE_QUOTATION → AWAIT_PAYMENT, the last with the system as actor.
  - Issuing twice gets 409, and calculating after issue gets 409.
  - Approvals reach FUNDS_VERIFIED only once DEPOSIT receipts cover 50L (PAYMENT receipts don't count); clearance follows.
  - Deposits approved before issue verify funds at issue.
  - A stale draft gets 409.
  - No GST rate gets 409, and so does the wrong stage.
  - A duplicate reference gets 409, whatever the case or spacing and on another request too.
  - Validation 400s, including a future received date.
  - A double approval gets 409; an unknown receipt gets 404.
  - With 4 concurrent approvals of one receipt, exactly 1 succeeds and 3 get 409.
  - ADMIN, MM and MH get 403 on every finance write. MH can read quotations and receipts; MM and ADMIN cannot. SUPERVISOR can issue.
- **`WorkflowTransitionsTest` (+1):** the finance guards with stubbed facts.

The frontend wasn't touched and the app wasn't started this phase. The finance endpoints are covered by MockMvc tests on Testcontainers postgres:16.

### Known issues / broken

- None blocking.
- **Business assumption to confirm with Finance:** funds are verified when approved **deposits** ≥ the request's **required deposit**, not the quotation total. See DECISIONS.md.
- There is no receipt rejection endpoint and no maker-checker rule yet, so the same Finance user may record and approve.
- The issued quotation snapshot, PDF and verification token are Phase 6.
- Still outstanding from Phase 2: Supervisor endpoints (cancel, overrides), regions/plants list endpoints, and the client IP on business audit rows.

### Next

Waiting for the next phase prompt. Nothing from later phases has been started.

---

## Phase 3B — Production, dispatch, completion, Admin Console APIs, Supervisor powers (2026-10-11) ✅

### New dependencies

None.

### What was done

- **Flyway V9:** `production_number_seq` and `dispatch_number_seq` (PRD-/DSP-<year>-<n>).
- **Production and dispatch:**
  - `POST /api/fleet-requests/{id}/schedule-production` (`PRODUCTION_SCHEDULE`): a production order for `productionRequired` units. It returns 409 when nothing needs producing.
  - `POST /api/fleet-requests/{id}/schedule-dispatch` (`DISPATCH_SCHEDULE`): from PRODUCTION_SCHEDULED, or from FINANCIALLY_CLEARED when `productionRequired = 0`.
  - `POST /api/fleet-requests/{id}/complete` (`DISPATCH_SCHEDULED` → COMPLETED): orders COMPLETED, dispatch DELIVERED, allocations CONSUMED, stock `allocated` reduced.
- **Supervisor:**
  - `POST /api/fleet-requests/{id}/override` (`WORKFLOW_OVERRIDE`): allow-listed fields, reason required, before/after audit, `is_override` history row.
  - `POST /api/fleet-requests/{id}/reassign` (`REQUEST_REASSIGN`): reason required.
  - `POST /api/fleet-requests/{id}/cancel` (`REQUEST_CANCEL`): releases stock and voids open quotations, orders and dispatches.
- **Admin Console:**
  - `/api/admin/users` (`ADMIN_USERS_MANAGE`): list/filter, get, create, patch, activate, deactivate. Deactivating revokes refresh tokens. Only SUPERVISOR manages SUPERVISOR accounts, and no self role change or self (de)activation.
  - `GET /api/admin/audit-logs` (`AUDIT_LOG_READ`): filter by actor, action, entity and date range.
- **Refactor:** `TransitionEffects` now holds the transition side effects (reject, cancel, complete). `WorkflowHistoryService.recordOverride` writes `is_override` rows. `StockAllocationService.consumeAll`.

### Commands run and real results

| Command | Result |
|---|---|
| `./mvnw -q -o compile` / `test-compile` | exit 0 |
| `./mvnw -q -o test -Dtest=OperationsAndSupervisorApiTest,AdminApiTest,MigrationSchemaTest` | **FAILED**: 3 test-side failures. Login events are also audited against entity USER, so the admin assertions needed filtering. One 403 check sent a body with the wrong shape, so validation answered 400 first (the documented ordering). |
| Re-run of those two classes after fixing the tests | exit 0 |
| `./mvnw -q -o verify` | **exit 1**: 1286 tests, 1 failure. The happy-path history assertion read an unsorted page, so the row order depended on the database. |
| Fix: explicit `createdAt, id` sort; re-run of the class | exit 0 |
| `./mvnw -q -o verify` (final) | **exit 0**. Surefire totals: **1286 tests, 0 failures, 0 errors**. |

**New tests:**
- **`OperationsAndSupervisorApiTest` (7):**
  - **Full happy path over HTTP, creation → COMPLETED.** It covers create → map 30/50 → qualify → forward → approve → forward → calculate → issue → deposit → approve (FUNDS_VERIFIED) → clearance. Dispatch before production gives 409. Then production (20 units), dispatch (50 units) and complete. Afterwards the order is COMPLETED, the dispatch DELIVERED, the stock shows allocated 0 and the allocation is CONSUMED. History has all 13 steps in order, and a later cancel gives 409.
  - Production skipped when everything comes from stock: 409, no order, then dispatch directly and complete.
  - Scheduling 403 for ADMIN, MM, MH and FIN; a past date gives 400.
  - **Override without a reason gives 400**, and so do non-allow-listed fields (status, quantity, allocatedQty, productionRequired, variantId), empty changes and a negative deposit. None of them write an audit row.
  - **Override audited:** the OVERRIDE row has the reason and before/after `requiredDeposit`, and history has `is_override=true`. Fleet roles and ADMIN get 403; a closed request gets 409.
  - **Cancel releases stock:** 30 units back to available, allocation RELEASED, `WORKFLOW_CANCEL` audited. No reason gives 400, other roles get 403, and a repeat gives 409.
  - Reassign: no reason gives 400, an ADMIN assignee gives 409, MH gets 403, and a success is audited.
- **`AdminApiTest` (5):**
  - **ADMIN gets 403 on all 31 operational endpoints** (sent with well-formed bodies), while `/api/admin/users`, `/api/admin/audit-logs` and `/api/dealers` return 200.
  - User CRUD: the password is never returned or audited and the user can log in. A bad username, weak password or duplicate is rejected, and a stale version gives 409.
  - Deactivation: the access token gets 401 immediately, refresh gets 401 and login gets 401; reactivation restores access.
  - ADMIN can't create, promote or deactivate a SUPERVISOR (403); self-deactivation and a self role change give 409; SUPERVISOR can create a SUPERVISOR. MM gets 403 on the Admin Console.
  - Audit search by entity, actor, action and date.

The frontend wasn't touched and the app wasn't started this phase. Everything is covered by MockMvc tests on Testcontainers postgres:16.

### Known issues / broken

- None blocking.
- **To confirm:** only SUPERVISOR can manage SUPERVISOR accounts (ADMIN gets 403). This is a security choice; see DECISIONS.md.
- **Still to confirm with Finance (from 3A):** funds are verified on approved deposits ≥ required deposit.
- No password reset/change endpoint yet, no receipt rejection, and no maker-checker rule.
- Regions/plants list endpoints and the client IP on business audit rows are still outstanding.

### Next

Waiting for the next phase prompt. Nothing from later phases has been started.

---

## Phase 4 — Frontend auth, app shell, motion, UI kit (2026-10-11) ✅

**New dependency:** `motion` 14.1.0. **Built:** Axios client (in-memory JWT, single-flight refresh on 401 with cross-tab Web Lock, `X-Requested-With`, session-expired hook) · auth via `/auth/me` query + `RequireAuth` context · login (RHF+Zod) / logout · `AppShell` (animated collapsible sidebar, mobile drawer, top bar with breadcrumbs, user/role, theme toggle, logout) · permission-filtered nav + `RequirePermission` guards + role landing page · 403 / 404 / route-error pages, `ErrorBoundary` per route · toasts (swipe to dismiss) · `src/lib/motion.ts` tokens, `MotionConfig reducedMotion="user"` · UI kit: Button, Badge/StatusBadge, Card, Drawer, Dialog, ConfirmDialog (reason), DataTable (server paging, sticky header, mobile cards), Pagination, Skeleton (shimmer), EmptyState, ErrorState, Form/TextField/SelectField/TextareaField · light/dark theme. Module pages are placeholders.

| Command | Result |
|---|---|
| `npx vitest --run src/features/auth` (1st/2nd) | **FAILED**: logout crashed TopBar (user gone before redirect); login never fetched `/me` (`fetchQuery` returned the fresh cached `null`). Fixed: user via `RequireAuth` context; `staleTime: 0`. Also `event.currentTarget` null in async invalid handler. |
| `npm test -- --run --reporter=dot` | **6 files, 34 tests passed** (auth 3, navigation/guards 15, api client 3, DataTable 8, ConfirmDialog 1, health 2) |
| `npm run build` | **OK**: `tsc -b` clean; 688 kB (219 kB gzip), Vite >500 kB chunk warning |
| `npm run lint` | **OK** after splitting hooks from component files (4 warnings → 0) |
| Live smoke: backend jar `dev` + Vite, curl via `:5173/api` proxy | login 200 → `/me` MARKETING_HEAD with 5 permissions; refresh without header 403, with header 200; logout 204. Headless Edge on `/finance` renders the Sign-in page. Servers stopped. |

**Broken / open:** none blocking. Bundle needs route-level code splitting (later). Responsive layouts verified by classes and tests only, not visually at 360/768/1024/1440. Dev role simulator not built (see DECISIONS).

**Next:** waiting for the Phase 5 prompt.

---

## Phase 5A — Dashboard cards, fleet table, request drawer (2026-10-11) ✅

**New dependencies:** none. **Backend:** `GET /api/fleet-requests/summary` (stage counts + units to produce, SQL `GROUP BY`), `GET /api/fleet-requests/{id}/production-orders`, `/dispatches`, `GET /api/regions`, `GET /api/plants`. **Frontend (`features/fleet`):** 6 count-up metric cards (click = stage filter) · fleet table with 10 columns, debounced search, status/stage, region, dealer, model, plant and date filters, sortable headers, server paging, mobile cards + sort select, all state in the URL · drawer (`?request=id`) with tabs Overview, Inventory, Finance, Payments, Workflow, Schedule, Audit, hidden by read permission and editable by role + state (edit details, map stock, calculate/issue quotation, record/approve receipts, schedule production/dispatch). Workspace shared by the four fleet modules, each opening on its stage.

| Command | Result |
|---|---|
| `./mvnw -q -o test -Dtest=FleetRequestReadApiTest` | 5 tests, 0 failures |
| `npx vitest --run src/features/fleet` | 1st run 1 failure (test expected "1–2 of 45"; page size is 20 → "1–20"); fixed; 17 passed |
| `npm test -- --run --reporter=dot` | **8 files, 51 tests passed** |
| `npm run build` / `npm run lint` | OK (759 kB bundle, chunk-size warning) / 0 findings after fixing 4 warnings |
| `./mvnw -q -o verify` | **exit 0 — 1291 tests, 0 failures, 0 errors** |
| Live: dev jar on :8080, curl as supervisor | summary, stage-filtered + `dealer.name` sorted list, regions, plants, production-orders, dispatches, request audit all 200; live JSON parsed by the frontend Zod schemas (throwaway test, removed) |

**Open:** workflow transition buttons, create-request form, Supervisor override/cancel UI are not built (next phase). SLA chips/“at risk” count are Phase 6. Bundle still needs code splitting. Not visually checked at 360/768/1024/1440.

**Next:** waiting for the Phase 5B prompt.

---

## Phase 5B — Workflow actions, executive queue, finance and scheduling flows (2026-10-11) ✅

**New dependencies:** none. **Backend:** unchanged. **Frontend:** drawer footer with workflow buttons from `allowedActions` — Marketing qualify / forward / resubmit, Executive approve / reject (reason required) / forward to Finance (the Executive Approval module is the queue, opening on its stage), Finance grant clearance, Supervisor complete; "Quotation…", "Receipts…", "Schedule production…/dispatch…" shortcuts open the tab forms. ConfirmDialog on every transition, receipt recording and scheduling; quotation view adds backend line items and terms; mutations invalidate `['fleet']` and toast. Fixed nested overlays (Escape closed the drawer under a dialog).

| Command | Result |
|---|---|
| `npx vitest --run …/WorkflowActions` (1st) | 2 failures: toast text also matched a status badge; fixed by naming the toast region |
| Live walk (dev jar on :18080 against scratch DB `fleet_p5bcheck`, same endpoints/bodies as the UI) | FLT-2026-0891 went map → qualify → forward → reject → resubmit → … → COMPLETED, 20 history rows; every `allowedActions` matched the UI map **except** Finance in PENDING_FINANCE_CLEARANCE (empty — ISSUE_QUOTATION's guard). Footer shortcuts changed to role + state rules; tests updated. Also confirmed: a fully-mapped stock row has 0 options (5A fix needed). Scratch DB dropped; dev DB untouched. |
| `npm test -- --run --reporter=dot` | **9 files, 72 tests passed** |
| `npm run lint` / `npm run build` | 0 findings / OK (766 kB, chunk warning) |
| Backend | not changed, not re-run (last full run 5A: 1291 tests, 0 failures) |

**Open:** Supervisor cancel / override / reassign UI; create-request form; SLA (Phase 6); bundle splitting; visual check at 360–1440 px.

**Next:** waiting for the next phase prompt.

---

## Phase 5C — Admin Console and Supervisor override (2026-10-11) ✅

**New dependencies:** none. **Backend:** `GET /api/admin/roles` (roles + permissions, read-only). **Frontend:** Admin Console with tabs Users (search/role/status filters, create with validation, edit, confirmed (de)activation, Supervisor-account and self rules), Roles (permissions per role), Dealers (onboarding form with GSTIN/PIN/phone/code checks, edit, confirmed status change), Audit log (filters, before/after detail). Fleet drawer: Supervisor override switch + red banner → override form (5 allow-listed fields, required reason, old-vs-new confirmation, `null` clears), override audit trail; Supervisor Cancel (reason) in the footer. `Tabs` and `reportError` moved to shared code.

| Command | Result |
|---|---|
| `./mvnw -q -o test -Dtest=AdminApiTest` | 6 tests, 0 failures (new roles test) |
| `npx vitest --run src/features` (after Cancel) | 2 failures — expected button lists lacked the new "Cancel request"; expectations updated |
| `npm test -- --run --reporter=dot` | **11 files, 92 tests passed** (override 8, admin console 12 new) |
| `npm run lint` / `npm run build` | 0 findings (after un-exporting a constant) / OK |
| `./mvnw -q -o verify` | **exit 0 — 1292 tests, 0 failures, 0 errors** |
| Live (dev jar on :18080, scratch DB `fleet_p5ccheck`) | override with UI body → 200, deposit 40L, estimate cleared; no reason → 400; as Admin → 403; audit OVERRIDE before/after keys match the trail; roles/users/dealers/audit JSON parsed by the frontend schemas. Scratch DB dropped. |

**Open:** Supervisor reassign UI; create-request form; password reset; SLA and dashboard (Phase 6); bundle splitting; visual check at 360–1440 px.

**Next:** waiting for the next phase prompt.

---

## Phase 6A — SLA timers (2026-10-11) ✅

**New dependencies:** none. **Backend:** V10 `sla_policies` (7 default stages, 75 % warning) + stage clock columns on `fleet_requests` (backfilled) + one-breach-per-entry unique index; `SlaService.enterStage` on every transition and create; `slaStatus` in list/detail, `sla=` filter, SLA counts in the summary; `SlaBreachJob` (5 min, conditional claim, system `SLA_BREACHED` history event); `GET/PATCH /api/admin/sla-policies` (Supervisor, audited). **Frontend:** SLA chip (server colour, client countdown, pulse when overdue) in table, drawer title and Overview; SLA filter with "Overdue"; per-module "N at risk · M overdue" button; minute refetch; Admin Console **SLA** tab (Supervisor only) with validated edit form.

| Command | Result |
|---|---|
| `./mvnw -q -o test -Dtest=MigrationSchemaTest,…` | 1 failure: expected migration v9 / 22 tables → updated to v10 / 23 |
| `./mvnw -q -o test -Dtest=SlaClockTest,SlaApiTest` | 1st: 1 test-side failure (asserted an empty actor list; the row's actor is `null`, as intended); fixed → **16 passed** |
| `npm test -- --run` (during work) | 1 fixture failure (summary fixture lacked SLA fields — strict schema), 2 test-side fixes (timer advanced twice; cached query not refetched) |
| `npm test -- --run --reporter=dot` | **13 files, 112 tests passed** |
| `npm run lint` / `npm run build` / `tsc -b` | 0 findings / OK / clean |
| `./mvnw -q -o verify` | **exit 0 — 1308 tests, 0 failures, 0 errors** |
| Live: `pg_dump` of the dev DB (v9) → scratch DB + one open request entered PAYMENT_PENDING 200 h ago; jar on :18080, scan every 10 s | V10 applied; backfill dated the stage from the AWAIT_PAYMENT row, due +168 h; the real scheduler recorded **one** SLA_BREACHED (no actor, frozen times) and later scans none; `?sla=BREACHED` returned it, summary finance breached 1; live JSON parsed by the frontend schemas. Scratch DB dropped; dev DB untouched (still v9 — V10 applies on your next backend start). |

**Open:** your running backend on :8080 is the old build — restart it to apply V10. Business-hours SLA and pausing are out of scope. Dashboard (6B/6C), quotation PDF, bundle splitting, visual check at 360–1440 px remain.

**Next:** waiting for the next phase prompt.

---

## Phase 6B — Quotation PDF (2026-10-11) ✅

**New dependencies:** `io.github.openhtmltopdf:openhtmltopdf-pdfbox` 1.1.86 (PDFBox 3.0.7), `org.thymeleaf:thymeleaf-spring6` 3.1.5.RELEASE (Boot-managed), `com.google.zxing:core` 3.5.4; bundled DejaVu Sans fonts (for ₹). Frontend: none. **Backend:** V11 (`verification_token`, `snapshot_json`, `pdf_sha256`, CHECKs + immutability trigger, token backfill); Thymeleaf→PDF renderer (Indian grouping, amount in words, QR, DRAFT watermark, byte-reproducible output); issue freezes snapshot + SHA-256 + random token in the same transaction; `GET /api/quotations/{id}/pdf` (QUOTATION_READ, audited `QUOTATION_PDF_DOWNLOADED`, hash headers); public `GET /api/public/quotations/verify/{token}` (minimal fields, 404 otherwise) with a 30/min per-client limiter (429 + Retry-After). **Frontend:** Finance tab "Download PDF"/"Draft PDF" + per-row PDF, issued SHA-256 shown; public `/verify/quotation/:token` page (Genuine / Not found / Too many checks).

| Command | Result |
|---|---|
| `./mvnw -q -o test -Dtest=IndianFormatTest,QuotationPdfRendererTest` | 1 failure: my expected words for 9,99,999 crore were wrong (test-side); fixed → 31 passed |
| `./mvnw -q -o test -Dtest=…DocumentApiTest,MigrationSchemaTest,…` | 1st: Hibernate validate rejected `char(64)` mapping → `@JdbcTypeCode(CHAR)`; 2nd: 3 test-side fixes (jsonb spacing, migration 11, firewall 400 for `%3B`) → all passed (8 API, 8 schema, 27 format, 2 limiter, 4 renderer) |
| `npx vitest --run …QuotationPdf.test.tsx` | 1st: duplicate button names (current quotation also in History) → dropped the history button for the current one → passed |
| `npm test -- --run --reporter=dot` / `npm run lint` / `npm run build` | **15 files, 117 tests passed** / 0 findings / OK (chunk-size warning as before) |
| `./mvnw -q -o verify` (final, after quieting openhtmltopdf's INFO logs) | **exit 0 — 1349 tests, 0 failures, 0 errors** |
| Visual: sample issued + draft PDFs rendered to PNG | Layout, ₹ grouping, words, QR and watermark checked by eye; watermark draws over the text in a light tint (z-index ignored by the renderer), still legible |

**Open:** not run live against the dev DB this phase (V11 applies on your next backend start; existing issued quotations freeze on first download). Rate limiter is per instance and trusts only the socket address. Executive Dashboard (6C), bundle splitting, visual check at 360–1440 px remain.

**Next:** waiting for the next phase prompt.

---

## Phase 6C — Executive Dashboard (2026-10-11) ✅

**New dependencies:** frontend `recharts` 3.10.1 + `react-is` 19.3.0 (peer). Backend: none. **Backend:** `analytics` feature — `GET /api/analytics/executive` (MH, SUP) with KPIs + previous period, 12-month value, funnel, time per stage (slowest flagged), top 10 dealers, demand by region/model, stock vs production, all in SQL; V12 history indexes; dev `AnalyticsDemoSeed` (40 consistent backdated requests, 5 more dealers, 2 regions, inactive demo yard); legacy-quotation PDF path now assigns a token if missing. **Frontend:** lazy-loaded Executive Dashboard — URL filters (30 days/Quarter/Year/Custom, region, plant), KPI strip with count-up and good/bad deltas, 6 Recharts widgets with table view + CSV, empty/loading/error states, validated chart colour tokens for light/dark; shared `CountUp`, `lib/csv`.

| Command | Result |
|---|---|
| `./mvnw -q -o test -Dtest=ExecutiveAnalytics*,AnalyticsDemoSeedTest,MigrationSchemaTest` | Hand-computed API fixture passed 1st time; seed test failed (no request left in review: a full run takes ~7 weeks so nearly all finished) → recent requests keep open stages, newest two await review → passed |
| `npx vitest --run src/features/analytics` | 2 test-side fixes (split "Slowest" label; 5xx retried once before error) → 9 passed; after lazy-loading, preload in `beforeAll` (30 s budget) |
| Live: jar (dev) on scratch DB `fleet_p6ccheck`, real seed, API as `marketing.head`, ADMIN → 403 | First seed: 35/41 requests overdue, 16 cancelled → tuned dwell times/model mix → 87% compliance, 21 completed, 8 cancelled, 12 open across 6 stages |
| Visual: headless Edge screenshots of the real page against that backend (light, dark, 360 px container) | Fixed: Region/Plant selects squeezed at 360 px; Stock card now spans both columns. Only overflow at 360 px is the Demand table scrolling inside its card. Harness files deleted, scratch DB dropped |
| `./mvnw -q -o verify` (1st) | **1 failure**: Flt0891WalkthroughTest saw 230 eligible Dusters (demo yard was active) → demo yard made inactive |
| `./mvnw -q -o verify` (final) | **exit 0 — 1361 tests, 0 failures, 0 errors** |
| `npm test -- --run --reporter=dot` / `npm run lint` / `npm run build` | **16 files, 126 tests passed** / 0 findings / OK (main 811 kB, dashboard chunk 410 kB; size warning remains) |

**Open:** restart your backend to apply V11–V12 and the new dev seed (it adds the demo data once). Main bundle is still >500 kB. Overdue KPI is event-based (breaches recorded in the period), not "currently overdue". All Phase 6 items are done.

**Next:** waiting for the next phase prompt.

---

## Fix — sign-in with another role landed on "Access denied" (2026-10-11) ✅

**Cause:** sign-out clears the session before the top bar's own redirect, so the auth guard sent `/login` the last page as `from`; the login page sent whoever signed in next there without a permission check. **Fix:** `landingFor(user, from)` — back to `from` only if the new user may open it, else their home. Login now has one redirect path (the `<Navigate>` after the session loads).

| Command | Result |
|---|---|
| New test against the old `LoginPage` | fails: `expected '/forbidden' to be '/finance'` (reproduces the bug) |
| `npx vitest --run src/features/auth src/app` | 28 passed (regression test + 7 `landingFor` cases) |
| `npm test -- --run --reporter=dot` / `npm run lint` / `npm run build` | **16 files, 134 tests passed** / 0 findings / OK |

Backend unchanged, not re-run (last: 1361 tests, 0 failures).

---

## Phase D1 — Dealer Portal, backend (2026-10-11) ✅

**No new dependencies.** CLAUDE.md has no Dealer Portal section; built from the phase prompt (choices in DECISIONS.md). **V13:** DEALER role + DEALER_PORTAL/CATALOGUE_MANAGE permissions, `users.dealer_id`, `set_password_tokens`, `dealer_orders` (+ `fleet_requests.dealer_order_id/line_no`), catalogue columns on `vehicle_models`, `payment_proofs`. **API:** `/api/dealer/*` — catalogue (active only), orders (one fleet request per line), order/request lists and details scoped to the dealer (others' ids → 404, simplified statuses), cancel while in review (workflow row; later → 409), payment-proof upload (PDF/PNG/JPEG by magic bytes, ≤5 MB → pending receipt), own issued-quotation PDF. Admin: `/api/admin/catalogue` CRUD (ADMIN, SUP), `POST /api/admin/users/{id}/set-password-link`, DEALER accounts need `dealerId`. Public: `/api/public/set-password[/check]` (hashed, 72 h, single use, rate-limited). Finance: `GET /api/payment-receipts/{id}/proof`. Dev seed: catalogue text + `dealer.<code>` per dealer (`DEV_DEALER_PASSWORD`).

| Command | Result |
|---|---|
| `./mvnw -q -o test -Dtest=WorkflowTransitionsTest,MigrationSchemaTest` | 1 failure: expected migration "12" → bumped to 13 (plus table/role counts, DEALER cancel row) |
| `-Dtest=DealerPortalApiTest` | 1st: 2 test-side bugs (JSON list matcher, varargs cast) → **8 passed** |
| `-Dtest=DealerPaymentApiTest` / `SetPasswordApiTest` / `DevDealerPortalSeederTest` | 1 compile fix (multipart builder type), 1 missing repo method in the test → **4 / 5 / 2 passed** |
| `./mvnw -q -o verify` (1st) | 1 failure: AdminApiTest role counts (5→6 roles, SUP 21→22 perms, ADMIN + CATALOGUE_MANAGE) → updated |
| `./mvnw -q -o verify` (final) | **exit 0 — 1582 tests, 0 failures, 0 errors** |

**Open / broken:** the current frontend's `ROLES` enum lacks DEALER, so after V13 the Admin Console Roles tab (and Users tab once dealer users exist) fails to parse — fix first in the frontend phase. No email delivery for set-password links (admin copies the URL). Not run live against the dev DB (V13 applies on your next backend start; add `DEV_DEALER_PASSWORD` to `backend/.env` for dealer logins). Frontend not touched or re-run.

**Next:** waiting for the next phase prompt (Dealer Portal frontend).
