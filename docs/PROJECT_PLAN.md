# Project Plan

Educational CDSS prototype (undergraduate software engineering assignment) —
synthetic data only — not for clinical use.

The build is split into eight steps, **A–H** (G and H were added on 2026-10-14 at the repository owner's request). Each step ends with working code,
passing tests, updated docs, a short report, and **approval from the
repository owner** before the next step starts. Architecture details are in
[`ARCHITECTURE.md`](ARCHITECTURE.md); working rules are in
[`../CLAUDE.md`](../CLAUDE.md).

## Goals

1. A complete, working CDSS that is easy to demonstrate:
   log in → open a synthetic patient → run a decision → see alerts and
   recommendations explained by the rules that fired → acknowledge them.
2. Clinical logic stored as **JSON rules** in a knowledge base and evaluated
   by straightforward Java code.
3. A project a student can **set up in minutes and explain**: one backend,
   one database, one frontend, no container or cloud tooling.

## Non-goals

- Clinical validation, regulatory compliance, production deployment.
- Real patient data, EHR/FHIR/HL7 integration.
- Docker, Kubernetes, microservices, cloud infrastructure, CI pipelines.
- JWT/OAuth token systems, API versioning, rule versioning history,
  audit-log subsystem, machine learning.

## Local setup the plan assumes

- JDK 21
- MySQL 8 installed locally (Homebrew, the MySQL installer, or similar)
- Node.js 22 or 24 LTS (for the frontend)

No Docker is required at any step.

## Step overview

| Step | Name | Status |
|------|------|--------|
| A | Get the Spring Boot backend running locally | **Done** |
| B | Connect it to MySQL | **Done** |
| C | Patient CRUD and knowledge-base APIs | **Done** |
| D | JSON rule engine and decision APIs | **Done** |
| E | React frontend connected to the backend | **Done** |
| F | Authentication, tests, and final demo | **Implemented** (main workflows confirmed in the browser; full checklist not formally ticked) |
| G | Medications, allergies, safety check, rule management, follow-ups | **Implemented** (V8–V10 applied locally) |
| H | Role workspaces, user management, profile menu, UI clean-up, detailed safety feedback | **Implemented** (V11–V12 applied locally; main workflows confirmed in the browser) |

---

## Step A — Get the Spring Boot backend running locally

**Done (2026-10-13):**
- Spring Boot 4.1 / Java 21 project with Maven Wrapper; `GET /api/health`;
  environment-based configuration; Flyway baseline migration;
  deny-by-default security.
- Docker removed: `docker-compose.yml`, Testcontainers dependencies and the
  Docker-only integration test deleted; `.env.example` cleaned up.
- Classes organised into `controller`, `service`, `dto`, `config` packages
  (behaviour unchanged).
- `./mvnw spring-boot:run` uses the `dev` profile by default.
- `./mvnw clean test` with the installed JDK 21: 11 tests, 0 failures,
  0 skipped. No Docker or MySQL needed.

Not yet verified: running against a real MySQL database (step B).

## Step B — Connect it to MySQL

- Install and start MySQL locally; create the `cdss` database and a
  `cdss_app` user (SQL in `backend/README.md`).
- Copy `.env.example` → `.env` and set the password.
- Run the backend (`./mvnw spring-boot:run`); Flyway applies `V1__baseline.sql`.
- **Done when:** `curl http://localhost:8080/api/health` returns `200` with
  `"database":"UP"`, and `flyway_schema_history` shows version 1.

**Done (2026-10-13):** MySQL 8.4.11 (Homebrew) on `localhost:3306`; database
`cdss` (utf8mb4); user `cdss_app@localhost` with privileges on `cdss.*` only;
password generated into the git-ignored `.env`. `./mvnw spring-boot:run`
started with the `dev` profile, Flyway applied `V1__baseline.sql`
(`success = 1`), and `GET /api/health` returned `200`
`{"status":"UP","components":{"application":"UP","database":"UP"}}`.
`./mvnw test`: 11 tests, 0 failures, 0 errors, 0 skipped.

## Step C — Patient CRUD and knowledge-base APIs

**Scope (as requested for step C):**
- **Patients:** create, list (with name search), get, update, delete.
  Fields: ID, full name, date of birth, gender, contact information (phone,
  email, address).
- **Knowledge base:** CRUD for drugs (name, dosage, category), diseases
  (name, ICD-10 code) and lab tests (name, numeric reference range, unit),
  with duplicate prevention.
- Flyway migrations, a global JSON error handler, and tests.

**Done (2026-10-13):**
- Migrations `V2__patients_and_knowledge_base.sql` (tables, primary keys,
  unique and check constraints) and `V3__sample_data.sql` (4 fictional
  patients; 5 drugs, 5 diseases, 5 lab tests labelled as educational
  examples) applied to the local MySQL database; Hibernate schema validation
  passed.
- Entities, repositories, services, DTOs and controllers for
  `/api/patients`, `/api/drugs`, `/api/diseases`, `/api/lab-tests`.
- `GlobalExceptionHandler`: 400 with field errors, 404, 409 for duplicates,
  generic 500.
- `dev` profile opens `/api/**` without login (`cdss.security.open-api`);
  other profiles keep them closed. Step F removes this.
- `./mvnw test`: 59 tests, 0 failures, 0 errors, 0 skipped (no database
  needed). A manual `curl` run against local MySQL checked every endpoint's
  success and error statuses, then deleted its test records.

**Moved to step D:** clinical rules (they are now planned to reference the
drugs, diseases and lab tests) and patient clinical records.

## Step D — JSON rule engine and decision APIs

**Done (2026-10-13):**
- **Clinical records:**
  - `V4__clinical_records.sql` adds vital signs (fixed units), lab results
    linked to knowledge-base lab tests, and patient conditions linked to
    diseases.
  - All three cascade when the patient is deleted.
  - Implausible vital signs and mismatched lab units are rejected with 400.
- **Rules:**
  - `V5__clinical_rules_and_decisions.sql` adds `clinical_rules`, with the
    condition and actions in MySQL JSON columns.
  - CRUD at `/api/rules`, plus `POST /api/rules/validate` and
    `GET /api/rules/facts`.
  - Plain-Java `ruleengine`: parser/validator, plus an evaluator with
    three-valued logic so missing data is never treated as normal.
- **Sample rules:** `V6__sample_rules.sql` seeds five illustrative rules
  (possible sepsis indicators, low SpO₂, tachycardia, high blood pressure,
  elevated fasting glucose) and sample conditions.
- **Decisions:**
  - `POST /api/patients/{id}/decisions` returns matched rules with reasons and
    actions, rules with insufficient data, invalid rules, a summary and a
    disclaimer.
  - Each decision is stored (`clinical_decisions`, `decision_rule_results`)
    and can be read back from the history endpoints.
- **Configuration:** observation age limits are set in `application.yml`
  (`cdss.decision.*`).
- **Automated tests:** `./mvnw test` gives 152 tests: 145 passed,
  0 failures, 0 errors, 7 skipped. The 7 skipped are the opt-in MySQL
  integration tests.
- **MySQL integration tests:** `CDSS_MYSQL_IT=true ./mvnw test
  -Dtest=MySqlIntegrationTests` against the local database: 7 passed,
  rolled back.
- **Manual check:** 20 HTTP checks against the running app all passed; test
  data was removed.

**Not done in this step (by design):** medications and allergies as patient
records, alert acknowledgement, editing clinical records (delete and re-add
instead).

## Step E — React frontend connected to the backend

**Change of plan:** the existing React frontend expected for this step was not
supplied (the repository and the GitHub remote contained none). As agreed with
the repository owner, a new frontend was built in `frontend/` instead.

**Done (2026-10-14):**
- **Stack:** React 19 + TypeScript (strict) + Vite 8 + Tailwind CSS 4, with
  React Router 7. No state-management, chart or UI-kit libraries.
- **Screens:** dashboard; patient list/search, form and detail; clinical
  panel (vital signs, lab results, conditions, request decision, decision
  history); clinical decisions page; drugs, diseases and lab tests; clinical
  rules (JSON editor with backend validation, enable/disable, available
  facts).
- **Backend connection:**
  - Every screen uses the real backend API through one client
    (`src/api/client.ts`), with base URL `VITE_API_BASE_URL` (default `/api`).
  - Vite proxies `/api` to `localhost:8080`, so no CORS change was needed.
  - **No backend files changed.**
- **Clinical safety:**
  - A disclaimer bar is shown on every page, and each decision shows the
    backend disclaimer.
  - "No configured rule matched" is explained as not meaning the patient is
    healthy.
  - Insufficient-data and invalid rules are listed separately.
- **Checks:**
  - `npm test`: 23 tests in 6 files pass.
  - `npm run build`: type check and production build succeed.
  - Every API call the screens make was exercised through the dev-server proxy
    against the running backend and MySQL (29/29 checks); test data was
    removed.
- **Not done in step E:** login (added in step F).

## Step F — Authentication, tests, and final demo

- `users` table (Flyway), BCrypt passwords, `ADMIN` and `CLINICIAN` roles.
  Demo users created at startup in the `dev` profile from `.env` values.
- `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me`
  with a session cookie (HttpOnly, `SameSite=Strict`).
- Lock down `SecurityConfig`: everything except health and login requires
  login; role rules as in `ARCHITECTURE.md`.
- Frontend login page, logged-in user display, logout, role-aware buttons.
- Tests: `@WebMvcTest` 401/403/200 checks for each endpoint group; login
  tests.
- `docs/DEMO.md`: a step-by-step demo script and manual test checklist.
- Final README pass: setup, run, demo accounts, known limitations.
- **Done when:** a fresh clone can be set up from the README and the demo
  script runs end to end.

**Implemented (2026-10-14), awaiting the manual demo run:**
- **Database:** `V7__users.sql` creates `users` (unique username, BCrypt
  `password_hash`, `role` CHECK `ADMIN`/`CLINICIAN`, `enabled`). No rows and
  no passwords in the migration; V1–V6 untouched.
- **Demo users:** `admin` and `clinician`, created at startup in the `dev`
  profile from `CDSS_DEMO_ADMIN_PASSWORD` / `CDSS_DEMO_CLINICIAN_PASSWORD`
  in `.env`, only if missing; existing users are never changed.
- **API:** `GET /api/auth/csrf`, `POST /api/auth/login`,
  `GET /api/auth/me`, `POST /api/auth/logout`. Session cookie HttpOnly +
  `SameSite=Strict`; session ID and CSRF token replaced on login.
- **Authorization:** URL rules in `SecurityConfig` for every endpoint
  (ADMIN-only: delete patient; change drugs, diseases, lab tests, rules;
  validate rules). The `cdss.security.open-api` flag is removed.
- **CSRF:** enabled (approved change from the original "CSRF disabled"
  plan); token stored in the session and sent as `X-CSRF-TOKEN`.
- **Frontend:** login page, route guard, user/role and **Log out** in the
  top bar, "session ended" handling, CSRF token handling with one retry,
  admin-only buttons hidden for clinicians.
- **Checks (2026-10-14):**
  - `./mvnw -o test`: 320 tests, 0 failures, 0 errors, 8 skipped (the opt-in
    MySQL integration tests, **not run**: they would apply V7 to the
    development database, which needs the owner's approval).
  - `npm test`: 37 tests in 8 files pass. `npm run build` succeeds.
  - Follow-up (approved): the `dev` profile binds the backend to
    `127.0.0.1`; the Vite proxy targets `127.0.0.1:8080`; HTTPS settings are
    documented only; login rate limiting remains an open limitation.
  - The application was **not started** during step F, so V7 has not been
    applied to the local database yet and the `docs/DEMO.md` checklist has
    not been run.


## Step G — Medications, allergies, safety check, rule management, follow-ups

Requested by the repository owner on 2026-10-14.

- **Medications (V8):** per-patient medication courses using knowledge-base
  drugs (dose, unit, route, frequency, start/end, status, notes). ACTIVE →
  DISCONTINUED (reason required) or COMPLETED; never deleted; one active
  course per drug. Medications tab with active and history lists.
- **Allergies (V8):** DRUG (linked drug), DRUG_CATEGORY (knowledge-base
  category), FOOD, ENVIRONMENTAL, OTHER; severity; ACTIVE → RESOLVED with a
  reason. Allergy history status: allergies recorded / no known allergies
  (explicitly confirmed) / not assessed. Allergies tab and a banner on the
  patient page.
- **Safety check (V9):** before saving a medication, compare it with the
  patient's active allergies, active medications (duplicates) and the
  interaction dataset, which is **two illustrative sample rows** labelled as
  such. Warnings needing review must be acknowledged with a reason; the
  backend enforces this (409). Never reports "safe".
- **Rule management:** activate (re-validates) / deactivate (always works),
  dry run against a patient, condition builder, plain-English preview,
  readable errors, search and status filter. Same JSON format and evaluator.
- **Follow-ups (V10):** schedule, reschedule, complete, cancel (reason);
  overdue/upcoming calculated on read; follow-ups page, patient tab and
  dashboard summary. No background jobs or notifications.
- **Done when:** all of the above work in the running app and the
  `docs/DEMO.md` checklist passes.

**Implemented (2026-10-14), awaiting the first run and the manual checklist:**
- `./mvnw -o test`: 460 tests, 0 failures, 0 errors, 9 skipped (the opt-in
  MySQL integration tests, **not run**: they would apply V7–V10 to the
  development database).
- `npm test`: 57 tests in 12 files pass. `npm run build` succeeds.
- Not yet verified: the migrations and entity mappings against MySQL
  (Hibernate validates them on the next start), and the browser flows.


## Step H — Role workspaces, user management and UI clean-up

Requested by the repository owner on 2026-10-14.

- **Separate workspaces:** clinicians get a clinical overview dashboard,
  patient-care navigation and read-only reference pages (including
  plain-English decision rules); administrators get an admin dashboard (live
  health, user, rule and catalogue figures), user management, rule
  management and catalogue editing, plus the clinical workflows. Admin pages
  show "access denied" to clinicians.
- **Backend:** rule JSON, facts, validation, dry runs and activation are
  ADMIN-only; clinicians use `GET /api/rules/summaries`. New
  `/api/admin/users` (list, create, activate, deactivate, change role) with
  self and last-admin guards; deactivation and role changes end the user's
  sessions. No migration was needed.
- **UI:** new branding, account menu (initials, username, role, My profile,
  Log out; keyboard and outside-click support), profile page, cleaner cards and
  quick actions, prototype/demo wording removed from the application chrome
  (the login page keeps the disclaimer; every decision and safety check shows
  the backend's disclaimer).
- **Detailed medication safety feedback:** each finding shows title, review
  priority, severity, medications, recorded allergens, explanation,
  mechanism and recommended action ("Not provided by the source" when
  absent) and a source label (patient record / illustrative sample /
  trusted source) with version and review date when recorded. "Check
  unavailable" and "drug not covered" states. Acknowledgement codes include
  a content fingerprint, so only unchanged findings keep their
  acknowledgement when the check is re-run after medication changes. V12 adds
  nullable source-detail columns to `drug_interactions`; no data was added.
- **Checks (2026-10-14, final):** `./mvnw test`: 499 tests, 0 failures, 0
  errors, 9 skipped (MySQL integration tests, not run). `npm test`: 75 tests
  in 17 files pass. `npm run build` succeeds. V11 and V12 applied to the
  local database on backend start; the owner confirmed the main clinician
  and administrator workflows in the browser.

---

## Risks and mitigations

| Risk | Mitigation |
|------|------------|
| Mistaken for a real clinical tool | Disclaimers in UI and docs; rules and interaction data labelled as illustrative |
| Sample interaction data mistaken for a real interaction database | Two rows only, labelled in the data, every result and the UI; "no match" never reported as safe |
| Wrong alerts from rule bugs | Small evaluator with thorough unit tests; alerts show which conditions matched |
| Unsafe rule content | Rules are data only; validator allows only known facts/operators; nesting limit |
| APIs open before step F | Resolved in step F: every endpoint except health, CSRF token and login needs a login |
| Project diagrams/requirements differ from what was built | Review them as soon as they are supplied; record deviations in the decision log |
| Setup problems on the demo machine | No Docker; one local MySQL; exact commands in `backend/README.md` |
