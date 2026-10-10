# Renault Group Fleet Portal — Project Instructions

This file is read at the start of every session. It holds the standing rules. Task-specific instructions arrive in the chat prompt for each phase.

## What we're building

A manufacturer/HQ-side B2B fleet management portal. It covers dealer onboarding, fleet requests, inventory and stock allocation, marketing qualification, executive approval, GST quotations, deposits and payment verification, production scheduling, dispatch, user and role admin, workflow history, and audited Supervisor overrides.

Monorepo: `frontend/` (React), `backend/` (Spring Boot), PostgreSQL via `docker-compose.yml`.

## Session protocol (always)

1. Read `PROGRESS.md` and `docs/DECISIONS.md` before doing anything else. Create them if they are missing.
2. Work only on the phase named in the prompt. Don't start the next phase.
3. After each meaningful step, run the relevant build or test command and fix failures before moving on.
4. End every session by updating `PROGRESS.md`: what was done, the commands run and their real results, what is broken, and what comes next. Then commit with a descriptive message.
5. Record every assumption or design choice in `docs/DECISIONS.md` as one line with its reason.
6. Never claim a build or test passed unless you ran it in this session and saw it pass. If something is blocked by the environment (no Docker, no network), say so plainly and continue with what can run.
7. Ask me only when a choice affects security or business correctness and isn't covered here. Otherwise decide, log it, and continue.

## Token and time efficiency (always)

- Don't re-read files you have already read this session. Use Grep or Glob to find code instead of reading whole folders.
- Change files with small Edits. Never rewrite a whole file to change a few lines.
- Follow the existing patterns in the codebase. Don't redesign anything that already works.
- Keep command output short:
  - `./mvnw -q`
  - `npm test -- --run --reporter=dot`
  - pipe long logs to `| tail -40`
- While working, run only the tests for what you just changed, e.g. `./mvnw -q test -Dtest=FinanceServiceTest`. Run the full suite once, at the end of the phase.
- Don't explain code back to me or narrate each step. Report only: what was built, the test results, and anything blocked.
- If the same error happens 3 times, stop, write it in PROGRESS.md and tell me. Don't keep retrying.
- Keep PROGRESS.md updates to about 10 lines per phase.

## Stack (use current stable, mutually compatible versions; record exact versions in PROGRESS.md)

- **Frontend:** React, TypeScript (strict), Vite, Tailwind CSS, React Router, TanStack Query, React Hook Form and Zod, Axios, Lucide React, Vitest with React Testing Library.
- **Backend:** Java 21, Spring Boot 3.x, Maven Wrapper, Spring Web/Security/Data JPA, Bean Validation, Flyway, springdoc-openapi, JUnit 5, Mockito, Testcontainers.
- **Database:** PostgreSQL 16. The schema comes only from Flyway migrations, so set `spring.jpa.hibernate.ddl-auto=validate`.

## Architecture rules

- **Backend:** package by feature under `com.renault.fleet.<feature>`, with controller, service, repository, entity, dto and mapper in each. Never expose entities from controllers. Use one `@RestControllerAdvice` that returns RFC 7807 `ProblemDetail`. All collection endpoints are paginated and filterable. Business logic lives in services, never in controllers.
- **Frontend:** use the feature folders under `src/features/*` and shared UI in `src/components/{ui,layout,forms,tables}`. All server state goes through TanStack Query hooks, with no duplicated API state. Invalidate the affected queries after every mutation. Don't use `any` unless a comment explains why.
- The backend is the only authority for permissions, validation, calculations and workflow. The frontend only mirrors these for user experience.
- Keep components under about 250 lines and avoid adding dependencies that aren't needed.

## Authentication (decided, don't re-litigate)

- The access token is a JWT that lives for 15 minutes. The frontend holds it **in memory only**, never in localStorage.
- The refresh token rotates on each use. It is stored in an `HttpOnly; Secure; SameSite=Strict; Path=/api/auth` cookie and kept **hashed** in the database so it can be revoked. Reusing a refresh token revokes that whole token family.
- CSRF protection: `/api/auth/refresh` and `/api/auth/logout` also require an `X-Requested-With` header.
- Roles come only from the authenticated principal on the server. A development role simulator is allowed in the UI, but only for display. It is behind the `dev` profile and grants nothing.
- Hash passwords with BCrypt. Development users are seeded only under the `dev` profile, with passwords read from environment variables. Never commit secrets, and keep only `.env.example` in git.

## Roles and access (enforce in Spring Security with method-level checks; mirror in routes and navigation)

| Role              | Modules                                 | Can do                                                                                                                                                                                |
| ----------------- | --------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| ADMIN             | Admin Console **only**                  | Manage users, roles, activation and dealer onboarding; view audit logs. **No** fleet, marketing, finance, production or dispatch access.                                              |
| SUPERVISOR        | All                                     | Everything the other roles can do, plus overrides, reassignment, cancellation and production/dispatch scheduling (see D1). Every override needs a reason and creates an audit record. |
| MARKETING_MANAGER | Fleet Evaluation & Stock Mapping        | Create and edit requests in PENDING_MARKETING_REVIEW, map stock, compute production needs, forward to the Marketing Head.                                                             |
| MARKETING_HEAD    | Executive Approval, Executive Dashboard | Approve or reject qualification, review deposits, forward to Finance, view the business analytics dashboard.                                                                          |
| FINANCE_TEAM      | Financial Clearance & Quotation         | Calculate and issue quotations, record and approve receipts, grant clearance.                                                                                                         |

Supervisor overrides can never bypass authentication, database constraints, the tax calculation, or duplicate-payment protection.

## Workflow state machine (backend-validated; each transition writes `workflow_history`)

```
PENDING_MARKETING_REVIEW   --MM: qualify (stock mapped)-->        MARKETING_QUALIFIED
MARKETING_QUALIFIED        --MM: forward-->                       PENDING_EXECUTIVE_APPROVAL
PENDING_EXECUTIVE_APPROVAL --MH: approve-->                       EXECUTIVE_APPROVED
PENDING_EXECUTIVE_APPROVAL --MH: reject (reason)-->               RETURNED_TO_MARKETING -> PENDING_MARKETING_REVIEW (on resubmit)
EXECUTIVE_APPROVED         --MH: forward-->                       PENDING_FINANCE_CLEARANCE
PENDING_FINANCE_CLEARANCE  --FIN: issue quotation-->              QUOTATION_ISSUED -> PAYMENT_PENDING (automatic)
PAYMENT_PENDING            --FIN: approve receipts ≥ required-->  FUNDS_VERIFIED
FUNDS_VERIFIED             --FIN: grant clearance-->              FINANCIALLY_CLEARED
FINANCIALLY_CLEARED        --SUP: schedule production-->          PRODUCTION_SCHEDULED  (skip if productionRequired = 0)
PRODUCTION_SCHEDULED       --SUP: schedule dispatch-->            DISPATCH_SCHEDULED
DISPATCH_SCHEDULED         --SUP: complete-->                     COMPLETED
any non-terminal           --SUP: cancel (reason)-->              CANCELLED (releases stock allocations)
```

Implement this as one `WorkflowService` with an explicit transition table of (from, action, to, allowed roles, guard). An invalid transition returns `409` with a ProblemDetail. A missing permission returns `403`.

## Business rules

- **Money:** use `NUMERIC(15,2)` columns and `BigDecimal` in Java. Round with `HALF_UP` to 2 decimal places per line, then sum. Never use float or double.
- **Production:** `productionRequired = max(0, requestedQty - allocatedEligibleStock)`.
- **Stock allocation:** lock the stock rows with `SELECT … FOR UPDATE` (pessimistic write lock) inside one transaction. A database `CHECK (available >= 0)` is the last line of defence. One unit of stock is never allocated to two requests.
- **Quotation:**
  - `base = qty × unitPrice`
  - `gst = base × rate`
  - `total = base + gst`
  - `depositShortfall = max(0, requiredDeposit − eligibleDeposits)`
  - GST rates live in a configurable `tax_rates` table that is effective-dated per vehicle category. The seed uses 18% for the Duster as given, but this rate **must be confirmed by Finance**; flag it in the README.
- **Quotation states:** DRAFT, ISSUED, then the payment lifecycle. Issuing a quotation is never payment. Only one ISSUED quotation per request is allowed, enforced with a partial unique index.
- **Payments:** the `transaction_reference` is unique. Approving a receipt locks the row and is idempotent. Approving it a second time returns `409`.
- Optimistic locking (`@Version`) on fleet requests, quotations and receipts.
- Every create, update, transition or override writes an `audit_logs` row containing the actor, action, entity, before/after JSON, reason and timestamp.

## Additional features (built in Phase 6)

### SLA timers

- An `sla_policies` table holds, for each workflow stage: the hours allowed, the warning threshold (default 75%), and an active flag. Only SUPERVISOR can edit it, from the Admin Console's SLA tab. Every change is audited.
- Default limits:

  | Stage                      | Hours allowed |
  | -------------------------- | ------------- |
  | PENDING_MARKETING_REVIEW   | 48            |
  | MARKETING_QUALIFIED        | 24            |
  | PENDING_EXECUTIVE_APPROVAL | 48            |
  | PENDING_FINANCE_CLEARANCE  | 72            |
  | PAYMENT_PENDING            | 168           |
  | FUNDS_VERIFIED             | 24            |
  | FINANCIALLY_CLEARED        | 72            |

- Hours are counted on the calendar, in IST (`Asia/Kolkata`). Log this as a decision; business-hours counting can come later.
- When a request enters a stage, store `stage_entered_at` and `sla_due_at` on the request. Pausing the clock is out of scope.
- The SLA status is calculated by the backend: `ON_TRACK`, `AT_RISK` (past the warning threshold) or `BREACHED`. It is returned in the request DTOs and is filterable. A scheduled job runs every 5 minutes and records a `SLA_BREACHED` event in `workflow_history` the first time a stage is breached. It must never record the same breach twice.
- Terminal states (`COMPLETED`, `CANCELLED`) have no SLA.
- **UI:**
  - A countdown chip on table rows and in the drawer, e.g. "18h left" or "Overdue 6h". It is green, amber or red, with a gentle pulse when breached.
  - An "Overdue" filter on the table.
  - Each user's view shows a count of requests at risk.
  - The countdown updates every minute on the client, but the status always comes from the server.

### Quotation PDF

- Generated by the backend only, using Thymeleaf HTML rendered to PDF with `openhtmltopdf`. QR codes come from ZXing.
- The PDF contents:
  - A text wordmark ("Renault Group Fleet Portal"), not a real logo.
  - Quotation number, issue date and validity (default 30 days).
  - Dealer details, line items, base amount, GST rate and GST amount, total, deposit received and shortfall.
  - The amount in words.
  - Indian number formatting (₹12,00,000.00).
  - Terms text, which is configurable.
  - A "DRAFT" watermark on drafts.
- When a quotation is issued, freeze its contents as an immutable JSON snapshot and store the SHA-256 hash of the PDF. Later downloads are regenerated from that snapshot and must produce identical figures.
- The QR code links to `/verify/quotation/{verificationToken}`, where the token is random and can't be guessed. This public page shows only the quotation number, issue date, total, status and a "Genuine" or "Not found" result. It never shows dealer contact details or deposits. The page is rate-limited.
- **Endpoints:**
  - `GET /api/quotations/{id}/pdf` for FINANCE_TEAM and SUPERVISOR; MARKETING_HEAD may view.
  - `GET /api/public/quotations/verify/{token}`, which is the only unauthenticated business endpoint.
- Every download is written to the audit log.

### Executive Dashboard

- Access: MARKETING_HEAD and SUPERVISOR. ADMIN gets 403.
- Uses Recharts on the frontend. All figures come from `GET /api/analytics/executive?from=&to=&region=&plant=`, computed in SQL by the backend, not the browser.
- **Widgets:**
  1. KPI strip: total order value (₹), number of requests, average time from request to clearance, SLA compliance %, and the number of overdue requests. Each shows the change against the previous period.
  2. Monthly order value: a line chart over the last 12 months.
  3. Pipeline funnel: the number of requests at each stage.
  4. Average time spent in each stage: a bar chart that highlights the slowest stage.
  5. Top 10 dealers by order value: a bar chart.
  6. Demand by region and by model: a bar chart or table.
  7. Stock against production: units supplied from stock compared with units that had to be produced.
- Filters: date range presets (30 days, quarter, year, custom), region and plant. The filters are stored in the URL query string so a view can be shared.
- Charts animate on load, have accessible labels, and show "No data for this period" when empty.
- Add an "Export CSV" button for the table widgets.
- The dev seed must create about 40 extra requests spread over the last 12 months, across dealers, regions and stages, so the charts have something to show.

## Seed data (dev profile only)

Request `FLT-2026-0891`:

- Chennai North Dealership, Chennai Region
- Renault Duster (RXZ Petrol), qty 50, yard stock 30, so production is 20
- Unit price ₹12,00,000, GST 18%
- Deposit available ₹10,00,000, required ₹50,00,000
- State PENDING_MARKETING_REVIEW, Pune Plant, estimate 6 weeks

Seed one user per role, plus 2–3 dealers, models, plants and inventory locations.

## UI direction

An original enterprise look:

- White and light-gray surfaces, a dark charcoal sidebar, and a yellow/gold accent.
- Clear status badges, accessible contrast, and a responsive layout with a mobile sidebar drawer.
- The shell has a sidebar, a top bar, breadcrumbs, the user's name and role, and logout.
- Navigation is filtered by the user's permissions.
- Every data view has loading skeletons, an empty state and an error state.
- Don't copy Renault's real website or use their logo files. Use a text wordmark.

## Motion and fluidity

Use the `motion` library (Framer Motion) for components and Tailwind transitions for simple hover and focus states. Motion should feel quick and purposeful, never slow or decorative.

**Timing:**

- 150–200 ms for small UI changes (hover, buttons, badges).
- 250–300 ms for panels, drawers and modals.
- Ease-out when entering, ease-in when leaving.
- Define these as shared tokens in `src/lib/motion.ts` so the timing is consistent everywhere.

**Where to animate:**

- **Page transitions:** fade with a slight upward slide (8px) when the route changes, using `AnimatePresence`.
- **Lists and tables:** rows and cards fade in with a short stagger (30 ms each, capped at 10 items). New rows slide in and removed rows fade out.
- **Metric cards:** numbers count up from 0 to their value on first load, with a hover lift (small shadow and 2px rise).
- **Drawers and modals:** drawers slide in from the right with a fading backdrop; modals scale from 95% to 100% with a fade. Both animate out when closing.
- **Sidebar:** collapse and expand smoothly on desktop; on mobile, slide over the content with a backdrop.
- **Tabs:** an underline indicator slides between tabs (`layoutId`), and tab content cross-fades.
- **Status badges:** cross-fade when the workflow state changes, with a brief highlight pulse on the changed row.
- **Workflow timeline:** steps reveal in order, and the current step has a gentle pulsing ring.
- **Toasts:** slide in from the top-right, fade out, and can be swiped away on mobile.
- **Buttons:** a subtle press effect (scale to 0.98) and an inline spinner while a request is loading.
- **Skeletons:** a shimmer effect, then cross-fade into the real content. Never let the layout jump.
- **Smooth scrolling:** set `scroll-behavior: smooth` globally, scroll to the top on route change, smoothly scroll to the first form error, and keep sticky table headers.

**Rules:**

- Respect `prefers-reduced-motion`: when it is set, turn off all movement and keep only opacity fades. Use `MotionConfig reducedMotion="user"`.
- Animate only `transform` and `opacity`, never width, height or top, to keep 60 fps.
- No animation may delay the user from clicking or typing.

## Responsive behaviour

Design mobile-first, with breakpoints at `sm` 640, `md` 768, `lg` 1024 and `xl` 1280.

- **Under `lg`:** the sidebar becomes a drawer opened from a hamburger button.
- **Under `md`:** tables turn into stacked cards showing the key fields, with a "View" button that opens the drawer.
- **Metric cards:** 1 column on phones, 2 on tablets, 3–6 on desktop.
- **Detail drawer:** full-screen on mobile, 640px wide on desktop.
- **Forms:** a single column on mobile.
- **Touch targets:** at least 44px.
- **Layout:** no horizontal page scrolling at 360px width. Test at 360, 768, 1024 and 1440.
- **Theme:** offer a light and dark mode toggle, defaulting to the system setting.

## Commands (keep this section current)

```
docker compose up -d postgres
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
cd backend && ./mvnw verify
cd frontend && npm install && npm run dev
cd frontend && npm run test -- --run && npm run build && npm run lint
```
