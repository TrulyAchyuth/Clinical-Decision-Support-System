# CLAUDE.md — Clinical Decision Support System (CDSS)

This file guides Claude (and any other contributor) working in this repository.
Read it fully before making changes. Where this file and other docs disagree,
this file wins; fix the other doc in the same change.

---

## 1. Project context

- **What this is:** An **undergraduate software engineering assignment**: a
  small, complete, easy-to-demonstrate Clinical Decision Support System.
  Users log in, manage synthetic patient records, maintain a knowledge base of
  JSON clinical rules, and ask the system for decisions (alerts and
  recommendations) about a patient.
- **Priority:** a working end-to-end demo that a student can **run, understand,
  and explain**. Prefer the simplest design that meets the requirements.
- **What this is not:** It is **not** a medical device, has **not** been
  clinically validated, and is **not** production-ready. It must never be used
  with real patient data or to inform real clinical decisions.
- **Data:** Synthetic patient data only. Never add, import, or request real
  patient data. Sample data must be obviously fictional.

### Mandatory wording rules

- Never describe the system as "clinically validated", "certified",
  "HIPAA-compliant", "production-ready", or "safe for clinical use".
- Example clinical rules are **illustrative** and must say so in their
  description. A rule match is a prompt for review — never call it a
  diagnosis, and never present "no rules matched" as "patient is well".
- Missing, stale or implausible data must never be treated as normal
  (see the three-valued logic in `docs/ARCHITECTURE.md` §5.4).
- The UI shows a visible disclaimer: *"Educational prototype — synthetic data
  only — not for clinical use."*

### Keep it simple — do NOT add

Docker / Docker Compose, Testcontainers, microservices, Kubernetes, cloud
infrastructure, message queues, caches, JWT/OAuth or other token systems,
GraphQL, API versioning schemes, expression/scripting languages for rules,
JSON Schema libraries, or end-to-end browser test frameworks. If something
seems to need one of these, ask the repository owner first.

---

## 2. Current status

Implementation follows steps **A–F** in `docs/PROJECT_PLAN.md`.

| Step | Name | Status |
|------|------|--------|
| A | Backend runs locally | **Done** — builds; 11 tests pass without Docker/MySQL |
| B | Connect to local MySQL | **Done** — local MySQL 8.4, Flyway V1 applied, health reports database UP |
| C | Patient CRUD + knowledge-base APIs (drugs, diseases, lab tests) | **Done** — 59 tests pass; endpoints checked against local MySQL |
| D | Patient clinical records, clinical rules, JSON rule engine + decision APIs | **Done** — 145 unit/web tests pass (+7 MySQL integration tests, opt-in, pass) |
| E | React frontend connected to the backend | **Done** — no existing frontend was supplied, so one was built in `frontend/`; 23 frontend tests pass |
| F | Authentication, tests, final demo | **Next** |

**Implemented so far:** Spring Boot backend on local MySQL, Flyway
migrations V1–V6, `GET /api/health`, patient CRUD, knowledge-base CRUD
(drugs, diseases, lab tests), patient clinical records (vital signs, lab
results, conditions), JSON clinical rules + plain-Java rule engine, decision
API with stored history. `/api/**` is open without login in the `dev`
profile only (`cdss.security.open-api`) until step F.
React frontend (`frontend/`) for every backend feature.
**No login exists yet.**
Keep documentation honest: anything not built and tested is labelled
*Planned*. Update this table and the README status at the end of every step.

---

## 3. Architecture decisions

Full detail lives in `docs/ARCHITECTURE.md` (including the decision log).

| Area | Decision |
|------|----------|
| Frontend | React + TypeScript + Vite + Tailwind CSS in `frontend/` (built in step E because no existing frontend was supplied). React Router for pages; no state-management or UI-kit libraries. |
| Backend | **One** Spring Boot app in `backend/`: Java 21, Spring Boot 4.1, Spring Web MVC, Spring Data JPA, Spring Security, Bean Validation. Maven Wrapper (`./mvnw`). |
| Layers | Controller → Service → Repository → Entity, with DTOs at the controller boundary. |
| Database | **Locally installed MySQL 8** (no Docker). Connection from environment variables. |
| Schema | Flyway SQL migrations run automatically on startup; Hibernate `ddl-auto=validate`. |
| Rule engine | Plain Java: JSON rules parsed with Jackson into Java records, checked by a hand-written validator, evaluated by a small recursive evaluator. |
| API | REST + JSON under `/api/...` (no version prefix). |
| Auth | Spring Security, username/password login, server-side HTTP session cookie, BCrypt, roles `ADMIN` and `CLINICIAN`. |
| Tests | JUnit 5 + Mockito unit tests and `@WebMvcTest` controller tests that run **without** a database; a short manual demo checklist for the full system. |

**There is exactly one backend.** Do not create a second backend (Node/Express,
Python, JSON-server mocks that duplicate business logic, etc.).

---

## 4. Repository layout

```
/
├── CLAUDE.md               # This file
├── README.md               # Project overview, status, quick start
├── .gitignore
├── .env.example            # Placeholder settings; copy to .env (git-ignored)
├── docs/
│   ├── PROJECT_PLAN.md     # Steps A–F, deliverables, done criteria
│   └── ARCHITECTURE.md     # Architecture, data model, API, rule engine, decisions
├── backend/                # Spring Boot app (see backend/README.md)
├── frontend/               # React app (see frontend/README.md)
└── tests/                  # README only — tests live inside backend/ and frontend/
```

Project diagrams and the requirements document, once supplied, go in
`docs/diagrams/` and `docs/requirements/`. The implementation should follow
them; document any deviation in the decision log in `docs/ARCHITECTURE.md`.

---

## 5. Coding conventions

### General
- Simple, readable code a student can explain line by line. No speculative
  abstractions, no dead code, no fake/stub implementations.
- No commented-out code. Comments explain *why*, not *what*.
- UTF-8, LF line endings, final newline.

### Java / Spring Boot
- Base package `com.cdss`, organised **by layer**:
  ```
  com.cdss
  ├── controller   # @RestController classes — HTTP only
  ├── service      # business logic, @Transactional
  ├── repository   # Spring Data JPA interfaces
  ├── entity       # @Entity classes (database tables)
  ├── dto          # request/response records + validation annotations
  ├── ruleengine   # rule model, validator, evaluator (plain Java, no Spring/JPA)
  ├── config       # SecurityConfig and other @Configuration
  └── exception    # custom exceptions + @RestControllerAdvice handler
  ```
- Controllers are thin: validate input, call a service, return DTOs.
  **Never return or accept JPA entities directly** in the API.
- Constructor injection only. Prefer `final` fields and Java `record`s for DTOs.
- Bean Validation (`@Valid`, `@NotBlank`, `@Size`, …) on every request DTO.
- One `@RestControllerAdvice` turns exceptions into clear JSON error responses
  (Spring `ProblemDetail`). Never return stack traces or SQL to clients.
- `java.time` types; timestamps stored in UTC.
- `ruleengine` classes take plain Java inputs and return plain results so they
  can be unit-tested without Spring or a database.
- Do not log passwords, session IDs, or patient clinical values.

### Database
- Each schema change is a new Flyway file in
  `backend/src/main/resources/db/migration` named `V<n>__<description>.sql`.
  Never edit a migration that has already been run; add a new one.
- `snake_case`, plural table names (`patients`, `clinical_rules`); foreign keys
  and `NOT NULL` where appropriate.

### TypeScript / React
- `strict` TypeScript; function components and hooks; one file per page in
  `src/pages`, shared pieces in `src/components`.
- All HTTP calls go through `src/api/client.ts`; `src/api/types.ts` mirrors the
  backend DTOs. Never invent endpoints or show mock data as if it were real.
- One base URL: `VITE_API_BASE_URL` (default `/api`, proxied by Vite to
  `localhost:8080`). Never put secrets in `VITE_*` variables.
- Show loading, empty, success and error states; display backend validation
  errors under the fields.
- Show the backend's disclaimer with every decision, and never word "no rule
  matched" as "healthy" or "normal".
- No `dangerouslySetInnerHTML` with user or rule content.

### API
- Resources under `/api` with plural nouns (`/api/patients/{id}/observations`).
- JSON `camelCase`; ISO-8601 dates.
- Status codes: 200/201/204 success, 400 validation, 401 not logged in,
  403 wrong role, 404 not found, 409 conflict.
- Keep the API table in `docs/ARCHITECTURE.md` in sync with the code.

### Git
- Do **not** commit or push unless the repository owner explicitly asks.
- Never commit `.env`, passwords, build outputs (`target/`, `node_modules/`,
  `dist/`), or real patient data.

---

## 6. Security requirements (sensible, not enterprise)

1. **Synthetic data only.**
2. **No secrets in the repo.** Database and demo-user passwords come from
   environment variables / the git-ignored `.env`. Only `.env.example` (with
   placeholders) is committed.
3. **Passwords** stored as BCrypt hashes.
4. **Authentication:** session-based login (`POST /api/auth/login`), logout,
   and `GET /api/auth/me`. No JWT or other token schemes.
5. **Authorization:** every endpoint except `/api/health` and
   `/api/auth/login` requires login once step F is complete. `ADMIN` manages
   the knowledge base (drugs, diseases, lab tests) and clinical rules, and can
   delete patients; `CLINICIAN` manages patient data and runs decisions. Enforce in `SecurityConfig` (URL rules) and/or
   `@PreAuthorize`.
6. **Session cookie:** HttpOnly and `SameSite=Strict`. CSRF tokens are
   disabled for the JSON API because `SameSite=Strict` stops browsers sending
   the cookie on cross-site requests (documented deviation; see
   `docs/ARCHITECTURE.md`).
7. **Input validation** on all request DTOs; rule JSON validated by the rule
   validator before it is saved or evaluated.
8. **Rule engine safety:** rules are data only. No `eval`, scripting, SpEL, or
   reflection driven by rule content; only allow-listed facts and operators.
9. **SQL:** Spring Data JPA / parameterised queries only.
10. **CORS:** prefer the frontend dev-server proxy (no CORS needed). If CORS is
    required, allow only the local frontend origin, never `*`.
11. **Errors:** generic messages to clients; details in server logs only.

---

## 7. Testing rules

- Every step ships with **basic** automated tests that run with
  `cd backend && ./mvnw test` and **do not need MySQL**:
  - **Rule engine:** unit tests for each operator, `all`/`any`/`not`, missing
    data, invalid rules, and each sample rule (fires / does not fire).
  - **Services:** unit tests with Mockito-mocked repositories.
  - **Controllers:** `@WebMvcTest` for validation errors, 404s, and (step F)
    401/403 role checks.
- **MySQL integration tests** (`MySqlIntegrationTests`) are opt-in:
  `CDSS_MYSQL_IT=true ./mvnw test`. They use the local database through the
  `dev` profile and roll back each test. Without the variable they are
  reported as skipped. When reporting results, state skipped tests and
  whether the MySQL tests were run.
- The whole system is also checked by running the app and following the demo
  checklist (`docs/DEMO.md`, created in step F).
- **Frontend:** Vitest + React Testing Library (`cd frontend && npm test`),
  with `fetch` replaced by `src/test/fetchMock.ts`, so no backend is needed.
  Run `npm run build` (type check + production build) as well.
- Never skip, weaken, or delete a failing test to get a green build; fix the
  cause or report it. Report test results honestly, including skipped tests.

Commands:

```bash
cd backend && ./mvnw test                                        # automated tests (no database)
cd backend && CDSS_MYSQL_IT=true ./mvnw test                     # also the MySQL integration tests
cd backend && ./mvnw spring-boot:run                             # run, dev profile (needs local MySQL)
cd frontend && npm test && npm run build                         # frontend tests, type check, build
cd frontend && npm run dev                                       # UI on http://localhost:5173
```

---

## 8. Workflow for each step

1. **Confirm scope** of the step with the repository owner.
2. **Implement** only that step. Don't pre-build later steps.
3. **Test** — write and run the tests; run the app if the step touches runtime
   behaviour.
4. **Verify** — inspect `git status` / `git diff`; no secrets or build outputs.
5. **Document** — update this status table, `README.md`, and
   `docs/ARCHITECTURE.md` (API table, decision log) to match reality.
6. **Report** — files changed, commands run, real test results, blockers, next
   step.
7. **Stop and wait for approval.** Do not commit or push unless asked.
