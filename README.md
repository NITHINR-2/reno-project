# Renault Group Fleet Portal

A manufacturer/HQ-side B2B fleet management portal. It covers dealer onboarding, fleet requests, inventory and stock allocation, marketing qualification, executive approval, GST quotations, deposits and payment verification, production scheduling, dispatch, user and role admin, workflow history, and audited Supervisor overrides.

> **Status:** Phase 1 (schema, JPA, auth). See [PROGRESS.md](PROGRESS.md) for current state and [docs/DECISIONS.md](docs/DECISIONS.md) for design decisions.

> ⚠️ **Finance confirmation required:** the seed GST rate of **18% for the Renault Duster** is a placeholder taken from the brief. Finance must confirm it before any quotation is treated as authoritative. Rates live in the effective-dated `tax_rates` table.

## Repository layout

```
backend/            Spring Boot 4 (Java 21, Maven Wrapper) — com.renault.fleet.<feature>
frontend/           React 19 + TypeScript (strict) + Vite 8 — src/features/*, src/components/*
docs/DECISIONS.md   Design decisions, one line each
docker-compose.yml  PostgreSQL 16 for local development
PROGRESS.md         Phase-by-phase progress log
```

## Prerequisites

- Java 21
- Node.js 24+ and npm 11+
- Docker with Docker Compose v2+ (also used by Testcontainers during `./mvnw verify`)

## Quick start

```bash
# 1. Local secrets (git-ignored). Use the SAME password in both files.
cp .env.example .env
cp backend/.env.example backend/.env
cp frontend/.env.example frontend/.env.local   # optional

# 2. Database
docker compose up -d postgres

# 3. Backend (http://localhost:8080)
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 4. Frontend (http://localhost:5173, proxies /api to the backend)
cd frontend && npm install && npm run dev
```

Open http://localhost:5173. The page shows the backend and database health from `GET /api/health`.

`backend/.env` must define `JWT_SECRET` (at least 32 bytes, e.g. `openssl rand -base64 48`) or the backend refuses to start.

## Authentication

| Endpoint | Notes |
|---|---|
| `POST /api/auth/login` | `{username, password}` → `{accessToken, tokenType, expiresIn, user}` and sets the refresh cookie |
| `POST /api/auth/refresh` | Needs the cookie **and** an `X-Requested-With` header. Rotates the cookie and returns a new access token |
| `POST /api/auth/logout` | Needs `X-Requested-With`. Revokes the session and clears the cookie |
| `GET /api/auth/me` | Current user, role and permissions |

- The access token is a 15-minute JWT. Keep it in memory only and send it as `Authorization: Bearer …`.
- The refresh token lives only in the `fleet_rt` cookie (`HttpOnly; Secure; SameSite=Strict; Path=/api/auth`). It rotates on every use, and replaying an old one revokes the whole session.
- All errors are RFC 7807 `application/problem+json`.

**Dev users** (dev profile only; passwords come from `DEV_*_PASSWORD` in `backend/.env`):

| Username | Role |
|---|---|
| `admin` | ADMIN |
| `supervisor` | SUPERVISOR |
| `marketing.manager` | MARKETING_MANAGER |
| `marketing.head` | MARKETING_HEAD |
| `finance` | FINANCE_TEAM |

**Dev business data** (dev profile only, idempotent): regions, plants, dealers, the vehicle catalogue, inventory, the Duster GST rate, and request **FLT-2026-0891**. It is Chennai North Dealership, Duster RXZ Petrol, qty 50, 30 units in the Chennai Yard, Pune Plant, 6 weeks, with a ₹50,00,000 deposit required and ₹10,00,000 received (an approved deposit receipt).

### Swagger walkthrough: FLT-2026-0891 to PENDING_FINANCE_CLEARANCE

1. `POST /api/auth/login` as `marketing.manager`, then click **Authorize** and paste the `accessToken`.
2. `GET /api/fleet-requests?q=FLT-2026-0891` and note the `id`.
3. `GET /api/fleet-requests/{id}/stock-options` and note the Chennai Yard stock `id` (30 available).
4. `PUT /api/fleet-requests/{id}/stock-allocations` with `{"lines":[{"stockId":<stock id>,"quantity":30}]}`. This gives allocated 30 and production required 20.
5. `POST /api/fleet-requests/{id}/submit-for-qualification`, then `POST /api/fleet-requests/{id}/forward-to-marketing-head`.
6. Log in as `marketing.head` and re-authorize.
7. `POST /api/fleet-requests/{id}/approve`, then `POST /api/fleet-requests/{id}/forward-to-finance`. The request is now `PENDING_FINANCE_CLEARANCE`.
8. `GET /api/fleet-requests/{id}/history` shows each step.

### Finance (Phase 3A), continuing as `finance`

1. `POST /api/fleet-requests/{id}/quotations/calculate` returns a DRAFT: base ₹6,00,00,000.00, GST 18% ₹1,08,00,000.00, total ₹7,08,00,000.00, shortfall ₹40,00,000.00.
2. `POST /api/quotations/{quotationId}/issue`. The request becomes `PAYMENT_PENDING` and the quotation stays `UNPAID`.
3. `POST /api/fleet-requests/{id}/payment-receipts` with `{"receiptType":"DEPOSIT","paymentMethod":"RTGS","transactionReference":"UTR-0001","amount":4000000.00,"receivedDate":"<today>"}`.
4. `POST /api/payment-receipts/{receiptId}/approve`. The deposits now cover ₹50,00,000, so the request becomes `FUNDS_VERIFIED`.
5. `POST /api/fleet-requests/{id}/grant-clearance`. The request becomes `FINANCIALLY_CLEARED`.

## Build and test

```bash
cd backend && ./mvnw verify                                        # unit + Testcontainers tests
cd frontend && npm run test -- --run && npm run build && npm run lint
```

## Useful URLs (dev)

| URL | What |
|---|---|
| http://localhost:5173 | Frontend |
| http://localhost:8080/api/health | Health check (public) |
| http://localhost:8080/swagger-ui.html | OpenAPI UI |

## Roles

ADMIN, SUPERVISOR, MARKETING_MANAGER, MARKETING_HEAD and FINANCE_TEAM. Roles and permissions are seeded by Flyway (`V6__seed_roles_permissions.sql`) and enforced on the server with `@PreAuthorize`. See `claude.md` for the access matrix and workflow state machine. The marketing, executive and finance stages of the workflow are implemented (Phases 2 and 3A); production and dispatch follow in later phases.
