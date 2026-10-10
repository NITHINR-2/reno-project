# Renault Group Fleet Portal

A manufacturer/HQ-side B2B fleet management portal. It covers dealer onboarding, fleet requests, inventory and stock allocation, marketing qualification, executive approval, GST quotations, deposits and payment verification, production scheduling, dispatch, user and role admin, workflow history, and audited Supervisor overrides.

> **Status:** Phase 0 (scaffold). See [PROGRESS.md](PROGRESS.md) for current state and [docs/DECISIONS.md](docs/DECISIONS.md) for design decisions.

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

ADMIN, SUPERVISOR, MARKETING_MANAGER, MARKETING_HEAD and FINANCE_TEAM. See `claude.md` for the access matrix and workflow state machine. *(Implemented in later phases.)*
