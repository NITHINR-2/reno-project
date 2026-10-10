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

ADMIN, SUPERVISOR, MARKETING_MANAGER, MARKETING_HEAD and FINANCE_TEAM. Roles and permissions are seeded by Flyway (`V6__seed_roles_permissions.sql`) and enforced on the server with `@PreAuthorize`. See `claude.md` for the access matrix and workflow state machine; the workflow itself arrives in a later phase.
