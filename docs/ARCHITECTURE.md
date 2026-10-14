# Architecture

Educational CDSS prototype (undergraduate software engineering assignment) —
synthetic data only — not for clinical use.

> **Status.** Implemented (steps A–D): backend on local MySQL, Flyway
> migrations V1–V6, `GET /api/health`, patient CRUD, knowledge base (drugs,
> diseases, lab tests), patient clinical records (vital signs, lab results,
> conditions), JSON clinical rules with a plain-Java rule engine, and the
> decision API with stored history. Everything marked *planned* is the design
> for steps E–F (see [`PROJECT_PLAN.md`](PROJECT_PLAN.md)).
>
> **Clinical safety:** all data is synthetic and every rule is an
> illustrative example — not clinically validated. A rule match is a prompt
> for clinician review, never a diagnosis. See §5.7.
>
> **Not yet reconciled:** the project's existing diagrams, requirements
> document, and React frontend are not in the repository yet. The data model
> and API below are a working proposal; when those materials are added, align
> this document with them and record any deviation in §10.

## 1. System overview

```
┌─────────────────────────────┐   HTTP + JSON, /api/...   ┌───────────────────────────────┐
│ Browser                     │   (session cookie after   │ Spring Boot backend (:8080)   │
│ Existing React + TypeScript │──── login, step F) ──────▶│                               │
│ + Tailwind app              │◀──────────────────────────│  controller  (REST endpoints) │
│ (frontend dev server)       │                           │  service     (business logic) │
└─────────────────────────────┘                           │  ruleengine  (plain Java)     │
   dev server proxies /api → localhost:8080               │  repository  (Spring Data JPA)│
                                                          │  entity      (tables)         │
                                                          └───────────────┬───────────────┘
                                                                          │ JDBC
                                                          ┌───────────────▼───────────────┐
                                                          │ MySQL 8, installed locally    │
                                                          │ database `cdss` (Flyway)      │
                                                          └───────────────────────────────┘
```

Three pieces, all run directly on the developer's machine — no Docker:

1. **Frontend** — the existing React app, started with its dev server.
2. **Backend** — one Spring Boot application, started with `./mvnw`.
3. **Database** — a locally installed MySQL server.

## 2. Frontend

Built in step E (no existing frontend was supplied). Details, screens and
the endpoint used by each screen: [`frontend/README.md`](../frontend/README.md).

- **Stack:** React 19, TypeScript (strict), Vite 8, Tailwind CSS 4,
  React Router 7. Tests: Vitest + React Testing Library.
- **Structure:**
  - `src/api/`: one `client.ts` for all HTTP calls, `types.ts` mirroring the
    backend DTOs, and one small file per resource.
  - `src/pages/`: one file per screen.
  - `src/components/`: layout, clinical panel, decision result, UI pieces.
- **Talking to the backend:**
  - Base URL `VITE_API_BASE_URL`, default `/api`.
  - The Vite dev server proxies `/api` to `http://localhost:8080`, so the
    browser sees a single origin. No CORS is configured in the backend, and
    the session cookie will work the same way in step F.
  - No secrets are present in, or needed by, the frontend.
- **Screens:**
  - dashboard;
  - patients (list, search, form, detail);
  - clinical panel with tabs: request decision, vital signs, lab results,
    conditions, decision history;
  - clinical decisions (choose a patient);
  - drugs, diseases, lab tests;
  - clinical rules (JSON editor + backend validation + facts list).
- **Behaviour:**
  - Every list has loading, empty and error states (with retry).
  - Forms show backend validation errors under each field.
  - Empty required fields are sent as `null` so the backend can name them.
- **Safety:**
  - A persistent disclaimer bar, and the backend disclaimer at the top of
    every decision.
  - A no-match result is shown as "No configured rule matched", with a note
    that it does not show the patient is healthy.

## 3. Backend

- **Stack:** Java 21, Spring Boot 4.1, Spring Web MVC, Spring Data JPA
  (Hibernate), Spring Security, Bean Validation, Flyway, MySQL Connector/J.
  Build with the Maven Wrapper.
- **Packages (by layer)** under `com.cdss`:

  | Package | Contains | Classes |
  |---------|----------|---------|
  | `controller` | `@RestController` classes | `HealthController`, `PatientController`, `DrugController`, `DiseaseController`, `LabTestController`, `ClinicalRecordController`, `ClinicalRuleController`, `DecisionController` (planned: `AuthController`) |
  | `service` | Business logic, `@Transactional` | `PatientService`, `DrugService`, `DiseaseService`, `LabTestService`, `ClinicalRecordService`, `ClinicalRuleService`, `PatientFactsService`, `DecisionService`, `DatabaseHealthChecker` |
  | `repository` | Spring Data JPA interfaces | one per entity |
  | `entity` | `@Entity` classes and enums | `Patient`, `Drug`, `Disease`, `LabTest`, `VitalSigns`, `LabResult`, `PatientCondition`, `ClinicalRule`, `ClinicalDecision`, `DecisionRuleResult`; `StringListJsonConverter` (list ↔ JSON column) |
  | `dto` | Request/response `record`s with validation | one request/response pair per resource, plus `DecisionResponse`, `RuleResultResponse`, `RuleValidationResponse`, `FactResponse` |
  | `ruleengine` | Plain-Java rule engine (no Spring/JPA) | `FactCatalog`, `FactDefinition`, `Operator`, `Condition`, `RuleParser`, `RuleError`, `RuleValidationException`, `PatientFacts`, `RuleEvaluator`, `Evaluation`, `Truth` |
  | `config` | Spring configuration | `SecurityConfig`, `TimeConfig`, `DecisionProperties` |
  | `exception` | Custom exceptions + global handler | `ResourceNotFoundException`, `DuplicateResourceException`, `ResourceInUseException`, `InvalidRequestException`, `GlobalExceptionHandler` |

- **Request flow:** Controller (validates DTO) → Service (logic,
  transaction) → Repository → MySQL. Services convert entities to DTOs;
  entities never leave the backend.
- **Configuration:** `application.yml` reads all database settings from
  environment variables (`CDSS_DB_HOST`, `CDSS_DB_PORT`, `CDSS_DB_NAME`,
  `CDSS_DB_USERNAME`, `CDSS_DB_PASSWORD`). The `dev` profile also loads the
  repository-root `.env` file, so a student only edits one file.
- **Errors:** `GlobalExceptionHandler` (`@RestControllerAdvice`) returns JSON
  `ProblemDetail` errors — `{type, title, status, detail, instance}` plus an
  `errors` object (field or rule path → message) for validation failures.
  Statuses: 400 invalid body/parameter/rule, 404 missing record or URL, 409
  duplicate or record still in use, 500 generic message (details logged
  server-side only).

## 4. Database

Locally installed **MySQL 8**, database `cdss`, character set `utf8mb4`.

- **Flyway is kept** because it already works with no extra setup: SQL files
  in `backend/src/main/resources/db/migration` run automatically when the
  backend starts, and `flyway_schema_history` shows what has been applied.
- Hibernate uses `ddl-auto=validate`: it checks the entities match the
  tables but never creates or drops anything.

### Migrations

| File | Step | Creates |
|------|------|---------|
| `V1__baseline.sql` | A | Database charset/collation |
| `V2__patients_and_knowledge_base.sql` | C | `patients`, `drugs`, `diseases`, `lab_tests` |
| `V3__sample_data.sql` | C | 4 fictional patients; 5 drugs, 5 diseases, 5 lab tests (illustrative) |
| `V4__clinical_records.sql` | D | `vital_signs`, `lab_results`, `patient_conditions` |
| `V5__clinical_rules_and_decisions.sql` | D | `clinical_rules`, `clinical_decisions`, `decision_rule_results` |
| `V6__sample_rules.sql` | D | 5 illustrative rules; sample conditions for two fictional patients |
| `V7__users.sql` | F (planned) | `users` |

All of V1–V6 are applied to the local database.

### Tables

| Table | Columns | Keys and constraints |
|-------|---------|----------------------|
| `patients` | `id`, `full_name`, `date_of_birth`, `gender`, `phone`, `email`, `address`, `created_at`, `updated_at` | PK; `gender` CHECK; index on `full_name` |
| `drugs` | `id`, `name`, `dosage`, `category`, `notes` | UNIQUE (`name`, `dosage`) |
| `diseases` | `id`, `name`, `icd10_code` | UNIQUE `icd10_code` |
| `lab_tests` | `id`, `name`, `reference_range_low`, `reference_range_high`, `unit`, `notes` | UNIQUE `name`; CHECK low ≤ high |
| `vital_signs` | `id`, `patient_id`, `recorded_at`, `systolic_bp`, `diastolic_bp`, `temperature`, `heart_rate`, `respiratory_rate`, `oxygen_saturation`, `notes`, `created_at` | FK `patient_id` → `patients` (CASCADE); CHECK at least one value present |
| `lab_results` | `id`, `patient_id`, `lab_test_id`, `value`, `unit`, `measured_at`, `notes`, `created_at` | FK → `patients` (CASCADE); FK → `lab_tests` (RESTRICT) |
| `patient_conditions` | `id`, `patient_id`, `disease_id`, `status`, `onset_date`, `notes`, `created_at` | FK → `patients` (CASCADE); FK → `diseases` (RESTRICT); UNIQUE (`patient_id`, `disease_id`); `status` CHECK |
| `clinical_rules` | `id`, `rule_code`, `name`, `description`, `severity`, `priority`, `enabled`, `condition_json` (JSON), `recommended_actions` (JSON), `created_at`, `updated_at` | UNIQUE `rule_code`; CHECK severity, priority 1–1000 |
| `clinical_decisions` | `id`, `patient_id`, `requested_at`, `outcome`, `rules_evaluated`, `summary` | FK → `patients` (CASCADE) |
| `decision_rule_results` | `id`, `decision_id`, `rule_id`, `rule_code`, `rule_name`, `severity`, `priority`, `result`, `reasons` (JSON), `recommended_actions` (JSON) | FK → `clinical_decisions` (CASCADE); FK → `clinical_rules` (SET NULL) |

- **Units** of vital signs are fixed: blood pressure mmHg, temperature °C,
  heart rate beats/min, respiratory rate breaths/min, oxygen saturation %.
  A lab result stores the unit of its lab test.
- **Deleting a patient** removes their records and decisions (CASCADE).
- **Deleting a lab test or disease** that patient records still use is
  refused with 409.
- **Decision history is self-contained:** each result copies the rule's code,
  name, severity, priority and actions, so it stays readable if the rule is
  edited or deleted later.
- MySQL stores JSON objects with its own key order, so a rule's `condition`
  may come back with keys in a different order. The meaning is unchanged.

## 5. Rule engine and decisions

All rule-engine code is in `com.cdss.ruleengine`. It is plain Java with no
Spring and no database, so it can be unit-tested directly. Nothing in a rule
is ever executed as code: no scripting, SpEL, `eval` or reflection.

### 5.1 How a decision is made

1. `POST /api/patients/{id}/decisions` arrives at `DecisionService`.
2. `PatientFactsService` collects the patient's **facts** (§5.3), ignoring
   observations older than the configured limits.
3. Every **enabled** rule is loaded in priority order. Its stored JSON is
   parsed and validated again by `RuleParser`. A rule that no longer
   validates (for example, its lab test was renamed) is reported as
   **invalid**, and the other rules still run.
4. `RuleEvaluator` evaluates each condition, giving one of:
   - **TRUE**: the rule matched.
   - **FALSE**: it did not match.
   - **UNKNOWN**: needed data was missing.
5. The decision is stored with one row per matched, insufficient-data or
   invalid rule. Rules that did not match are counted.
6. The response lists matched rules (with reasons and recommended actions),
   rules with insufficient data (with what is missing), invalid rules, a
   summary, and a disclaimer.

### 5.2 Rule JSON format

This is what `POST /api/rules` and `PUT /api/rules/{id}` accept and what
`GET /api/rules` returns (plus `id`, `createdAt`, `updatedAt`):

```json
{
  "ruleId": "BP-HIGH",
  "name": "High blood pressure reading",
  "description": "Systolic 140 mmHg or above, or diastolic 90 mmHg or above. Educational example.",
  "severity": "MEDIUM",
  "priority": 40,
  "enabled": true,
  "condition": {
    "any": [
      { "fact": "vitals.systolicBp",  "operator": "gte", "value": 140 },
      { "fact": "vitals.diastolicBp", "operator": "gte", "value": 90 }
    ]
  },
  "recommendedActions": [
    "Repeat the measurement after the patient has rested for 5 minutes",
    "Review previous blood pressure readings and current medications"
  ]
}
```

| Field | Required | Rules |
|-------|:--------:|-------|
| `ruleId` | ✓ | Letters, digits, `-`, `_`; up to 50 characters. Stored in upper case; unique. |
| `name` | ✓ | Up to 150 characters |
| `description` | | Up to 1000 characters; should say the rule is an educational example |
| `severity` | ✓ | `LOW`, `MEDIUM` or `HIGH` |
| `priority` | ✓ | 1–1000; **1 is the most important**. Results are listed in priority order. |
| `enabled` | | Default `true`. Disabled rules are not evaluated. |
| `condition` | ✓ | A condition object (below) |
| `recommendedActions` | ✓ | 1–10 non-empty strings, up to 300 characters each |

### 5.3 Conditions

A condition is one of these JSON objects, nested up to **6 levels** and with
at most **50 conditions** in one rule:

| Shape | Meaning |
|-------|---------|
| `{"all": [c1, c2, ...]}` | AND: every condition must be true |
| `{"any": [c1, c2, ...]}` | OR: at least one must be true |
| `{"not": c}` | NOT: `c` must be false |
| `{"fact": "...", "operator": "...", "value": ...}` | Compare one fact with a value |

**Facts** (`GET /api/rules/facts` lists them, including the lab facts):

| Fact | Type | Unit | Value used |
|------|------|------|-----------|
| `patient.ageYears` | number | years | Age in whole years on the day of the decision |
| `vitals.systolicBp`, `vitals.diastolicBp` | number | mmHg | Most recent value recorded within `vital-signs-max-age` |
| `vitals.temperature` | number | °C | " |
| `vitals.heartRate` | number | beats/min | " |
| `vitals.respiratoryRate` | number | breaths/min | " |
| `vitals.oxygenSaturation` | number | % | " |
| `lab.<lab test name>` | number | the lab test's unit | Most recent result for that knowledge-base lab test within `lab-results-max-age`, e.g. `lab.Fasting plasma glucose` |
| `conditions` | code list | — | ICD-10 codes of the patient's **ACTIVE** conditions |

Fact names are matched without regard to upper/lower case.

**Operators:**

| Operator | Meaning | Allowed with |
|----------|---------|--------------|
| `gt`, `gte` | greater than, greater than or equal to | number facts |
| `lt`, `lte` | less than, less than or equal to | number facts |
| `eq`, `neq` | equal to, not equal to | number facts |
| `contains` | the list includes the code (e.g. `"E11.9"`) | `conditions` |

`value` must be a JSON number for number facts and a code string for
`conditions`. Numbers are compared exactly (`38.0` is not greater than `38.0`).

### 5.4 Missing data: three-valued logic

A comparison on a fact with no recent value is **UNKNOWN**. It is never
treated as false or normal. Groups combine like this:

| Group | Result |
|-------|--------|
| `all` | FALSE if any child is FALSE; otherwise UNKNOWN if any child is UNKNOWN; otherwise TRUE |
| `any` | TRUE if any child is TRUE; otherwise UNKNOWN if any child is UNKNOWN; otherwise FALSE |
| `not` | TRUE ↔ FALSE; UNKNOWN stays UNKNOWN |

So a rule can still match when only some data is missing (one true branch of
an `any` is enough). A rule that ends up UNKNOWN is reported under
`insufficientData` with messages such as *"No heart rate recorded in the last
24 hours"*.

Observation age limits are configuration, not code (`application.yml`):

```yaml
cdss:
  decision:
    vital-signs-max-age: 24h
    lab-results-max-age: 72h
```

Rule thresholds and actions live in the rules themselves (database), so they
can be changed through the API without touching Java code.

### 5.5 Validation

`RuleParser` checks every rule before it is saved (`POST`/`PUT /api/rules`),
when asked (`POST /api/rules/validate`), and again before each evaluation.
It reports **every** problem with its location:

```json
{
  "status": 400, "title": "Bad Request", "detail": "Invalid rule",
  "errors": {
    "condition.any[1].fact": "unknown fact \"vitals.pulse\"",
    "condition.any[2].all[0].operator": "must be one of gt, gte, lt, lte, eq, neq, contains"
  }
}
```

It rejects:
- input that isn't a JSON object;
- unknown facts (including lab tests not in the knowledge base);
- unknown operators, and operators that don't suit the fact;
- non-numeric values for number facts (including `true` or `"100"`);
- a missing `value`, or unknown extra fields;
- empty `all`/`any` lists, `not` given a list, or a group mixed with other keys;
- nesting deeper than 6 levels or more than 50 conditions.

### 5.6 Explanations

Each matched rule lists the statements that made it match, for example:

> Heart rate is 118 beats/min (recorded 2026-10-13 17:58 UTC), which is greater than 90 beats/min.
> Active conditions (ICD-10 codes) do not include I10.

For `any`, only the true branches are listed. For `all`, every condition is
listed. Each statement appears once.

### 5.7 Sample rules (V6, illustrative only)

| ruleId | Severity | Priority | Condition |
|--------|----------|---------:|-----------|
| `SEPSIS-SCREEN` | HIGH | 10 | Two or more of: temperature > 38.0 °C or < 36.0 °C; heart rate > 90; respiratory rate > 20 (simplified SIRS-style screen) |
| `SPO2-LOW` | HIGH | 20 | Oxygen saturation < 92 % |
| `HR-HIGH` | MEDIUM | 30 | Heart rate > 100 beats/min |
| `BP-HIGH` | MEDIUM | 40 | Systolic ≥ 140 mmHg **or** diastolic ≥ 90 mmHg |
| `GLUCOSE-HIGH` | MEDIUM | 50 | Fasting plasma glucose ≥ 7.0 mmol/L |

The possible-sepsis condition as stored:

```json
{"any": [
  {"all": [{"any": [{"fact": "vitals.temperature", "operator": "gt", "value": 38.0},
                    {"fact": "vitals.temperature", "operator": "lt", "value": 36.0}]},
           {"fact": "vitals.heartRate", "operator": "gt", "value": 90}]},
  {"all": [{"any": [{"fact": "vitals.temperature", "operator": "gt", "value": 38.0},
                    {"fact": "vitals.temperature", "operator": "lt", "value": 36.0}]},
           {"fact": "vitals.respiratoryRate", "operator": "gt", "value": 20}]},
  {"all": [{"fact": "vitals.heartRate", "operator": "gt", "value": 90},
           {"fact": "vitals.respiratoryRate", "operator": "gt", "value": 20}]}
]}
```

An example using `not` and `conditions`:

```json
{"all": [
  {"fact": "lab.HbA1c", "operator": "gte", "value": 6.5},
  {"not": {"fact": "conditions", "operator": "contains", "value": "E11.9"}}
]}
```

i.e. "HbA1c 6.5 % or above with no active type 2 diabetes code recorded".

**Clinical safety limitations:**
- Thresholds are simplified teaching examples. They are not clinically
  validated and must not guide real care.
- A match is **not a diagnosis**. The wording ("possible", "reading",
  "elevated") and the disclaimer in every decision response say so.
- "No rules matched" does **not** mean the patient is well. The summary says
  so, and rules lacking data are listed separately.
- Only recorded, recent data is used. The rules do not see free-text notes,
  symptoms, mental status, medications or trends.
- Implausible vital signs are rejected when recorded (§7). Plausible but
  wrong entries cannot be detected.

## 6. Security

- **Login (step F):** `POST /api/auth/login` with JSON username/password;
  Spring Security stores the login in a server-side **HTTP session**
  (cookie `JSESSIONID`, HttpOnly, `SameSite=Strict`). Logout invalidates the
  session. No JWTs.
- **Passwords:** BCrypt. Demo users are created at startup in the `dev`
  profile from `.env` values (`CDSS_DEMO_ADMIN_PASSWORD`,
  `CDSS_DEMO_CLINICIAN_PASSWORD`); no passwords in source code.
- **Roles (planned, step F):**

  | Action | CLINICIAN | ADMIN |
  |--------|:---------:|:-----:|
  | View/search patients | ✓ | ✓ |
  | Create/update patients | ✓ | ✓ |
  | Delete patients | | ✓ |
  | View knowledge base (drugs, diseases, lab tests) and rules | ✓ | ✓ |
  | Create/update/delete knowledge-base entries and rules | | ✓ |
  | Record vital signs, lab results, conditions | ✓ | ✓ |
  | Run decisions, view decision history | ✓ | ✓ |

- **CSRF:** disabled for the JSON API *(implemented)*. Once login exists,
  protection comes from the `SameSite=Strict` session cookie (browsers don't
  send it on cross-site requests). This is a deliberate simplification for a
  local assignment.
- **Until step F *(implemented)*:** the default configuration denies every
  path except `/api/health` (401). The `dev` profile sets
  `cdss.security.open-api=true`, which opens `/api/**` so the APIs can be
  used before login exists; the backend logs a warning when this is on.
  Step F removes the flag.
- Input validation on all DTOs; rule validation before saving and before
  each evaluation; JPA only (no
  string-built SQL); generic error messages to clients.

## 7. API

All endpoints are under `/api`, JSON in and out. Lists return plain JSON
arrays (the data set is small, so no pagination).
"Login" = requires login once step F is complete; until then these endpoints
are open in the `dev` profile only.

### Implemented

| Method | Path | Access | Success | Errors |
|--------|------|--------|---------|--------|
| GET | `/api/health` | public | 200 (503 if database down) | — |
| GET | `/api/patients?search=` | login | 200 list (sorted by name; `search` matches part of the name) | — |
| GET | `/api/patients/{id}` | login | 200 | 400 bad id, 404 |
| POST | `/api/patients` | login | 201 + `Location` | 400 |
| PUT | `/api/patients/{id}` | login | 200 | 400, 404 |
| DELETE | `/api/patients/{id}` | ADMIN (F) | 204 | 404 |
| GET / POST | `/api/drugs` | login / ADMIN (F) | 200 / 201 | 400, 409 |
| GET / PUT / DELETE | `/api/drugs/{id}` | login / ADMIN (F) | 200 / 200 / 204 | 400, 404, 409 |
| GET / POST | `/api/diseases` | login / ADMIN (F) | 200 / 201 | 400, 409 |
| GET / PUT / DELETE | `/api/diseases/{id}` | login / ADMIN (F) | 200 / 200 / 204 | 400, 404, 409 |
| GET / POST | `/api/lab-tests` | login / ADMIN (F) | 200 / 201 | 400, 409 |
| GET / PUT / DELETE | `/api/lab-tests/{id}` | login / ADMIN (F) | 200 / 200 / 204 | 400, 404, 409 |
| GET / POST | `/api/patients/{id}/vital-signs` | login | 200 (newest first) / 201 | 400, 404 |
| DELETE | `/api/patients/{id}/vital-signs/{recordId}` | login | 204 | 404 |
| GET / POST | `/api/patients/{id}/lab-results` | login | 200 (newest first) / 201 | 400, 404 |
| DELETE | `/api/patients/{id}/lab-results/{recordId}` | login | 204 | 404 |
| GET / POST | `/api/patients/{id}/conditions` | login | 200 / 201 | 400, 404, 409 |
| DELETE | `/api/patients/{id}/conditions/{recordId}` | login | 204 | 404 |
| GET | `/api/rules` | login | 200 (priority order) | — |
| GET | `/api/rules/{id}` | login | 200 | 400, 404 |
| GET | `/api/rules/facts` | login | 200: facts and allowed operators | — |
| POST | `/api/rules/validate` | ADMIN (F) | 200 `{valid, errors}` (nothing saved) | 400 for missing fields |
| POST | `/api/rules` | ADMIN (F) | 201 | 400 (fields or invalid rule), 409 |
| PUT / DELETE | `/api/rules/{id}` | ADMIN (F) | 200 / 204 | 400, 404, 409 |
| POST | `/api/patients/{id}/decisions` | login | 201 + `Location`: the stored decision | 400, 404 |
| GET | `/api/patients/{id}/decisions` | login | 200: history, newest first | 404 |
| GET | `/api/patients/{id}/decisions/{decisionId}` | login | 200 | 404 |

Request bodies (`*` = required):

| Resource | Fields |
|----------|--------|
| Patient | `fullName`* (≤150), `dateOfBirth`* (`YYYY-MM-DD`, not in the future), `gender`* (`MALE`/`FEMALE`/`OTHER`/`UNKNOWN`), `phone` (7–30 of digits, spaces, `+ ( ) - .`), `email`, `address` (≤255) |
| Drug | `name`*, `dosage`*, `category`*, `notes` — unique by name + dosage |
| Disease | `name`*, `icd10Code`* (e.g. `I10`, `E11.9`; stored upper-case) — unique by code |
| Lab test | `name`*, `referenceRangeLow`*, `referenceRangeHigh`* (numbers, low ≤ high, 2 decimals), `unit`*, `notes` — unique by name |
| Vital signs | `recordedAt`* (ISO-8601 instant, not in the future); at least one of `systolicBp` (40–300), `diastolicBp` (20–200), `temperature` (25.0–45.0, 1 decimal), `heartRate` (20–300), `respiratoryRate` (4–80), `oxygenSaturation` (50–100); blood pressure needs both values and systolic > diastolic; `notes` |
| Lab result | `labTestId`* (knowledge-base lab test), `value`* (≥ 0, 2 decimals), `measuredAt`* (not in the future), `unit` (optional; must equal the lab test's unit, no conversion), `notes`. Response adds `flag` LOW/NORMAL/HIGH against the reference range. |
| Condition | `diseaseId`* (knowledge-base disease), `status` (`ACTIVE` default / `RESOLVED`), `onsetDate` (not in the future), `notes` — once per patient and disease |
| Rule | see §5.2 |

Text is trimmed; empty optional fields are stored as `null`. Updates (`PUT`)
replace all fields. Clinical records are added and deleted (no update); to
correct one, delete it and add it again.

### Planned

| Step | Method | Path | Access | Purpose |
|------|--------|------|--------|---------|
| F | POST | `/api/auth/login` | public | Log in (starts session) |
| F | POST | `/api/auth/logout` | login | Log out |
| F | GET | `/api/auth/me` | login | Current user and role |

## 8. Local development

| Piece | How it runs |
|-------|-------------|
| MySQL | Installed locally (e.g. `brew install mysql` + `brew services start mysql` on macOS, MySQL Installer on Windows). One-time SQL creates the `cdss` database and `cdss_app` user — see `backend/README.md`. |
| Backend | `cd backend && ./mvnw spring-boot:run` (uses the `dev` profile, reads `../.env`) |
| Frontend | `cd frontend && npm install && npm run dev` → http://localhost:5173 (proxies `/api` to the backend) |

## 9. Testing strategy

| Level | Tooling | Covers | Needs MySQL? |
|-------|---------|--------|:------------:|
| Rule engine | JUnit 5 (incl. parameterized) | Parser (valid, invalid, limits), evaluator (each operator at boundaries, AND/OR/NOT, missing data), every sample rule read from `V6__sample_rules.sql` | No |
| Services | JUnit 5 + Mockito | Patients, knowledge base, clinical records, rules, facts collection, decisions | No |
| Controllers | `@WebMvcTest` + Spring Security Test | Status codes, validation errors, 404/409, response shapes; 401/403 per role (F) | No |
| Integration | `@SpringBootTest` (`MySqlIntegrationTests`) | Real MySQL: migrations, JSON columns, constraints, cascades, full decision flow. Each test is rolled back. | **Yes**, opt-in: `CDSS_MYSQL_IT=true ./mvnw test` |
| Frontend | Vitest + React Testing Library, `fetch` mocked (`cd frontend && npm test`) | API client errors, decision display wording, patient list/form, rules page validation | No |
| Whole system | Manual demo checklist (`docs/DEMO.md`, step F) | Browser flows | Yes |

`cd backend && ./mvnw test` runs everything that doesn't need MySQL. The
MySQL integration tests show as *skipped* unless `CDSS_MYSQL_IT=true` is set.

## 10. Decisions and deviations

### Current decisions

| Date | Decision | Reason |
|------|----------|--------|
| 2026-10-13 | React + TS + Tailwind frontend, Spring Boot backend, MySQL | Assignment requirement |
| 2026-10-13 | Exactly one backend | No duplicated business logic |
| 2026-10-13 | Spring Boot 4.1.1, Java 21 | Current stable versions |
| 2026-10-13 | Custom `GET /api/health` reporting the database separately | Clear status without extra libraries |
| 2026-10-13 | Plain-Java rule evaluator; no expression languages | Safe, testable, easy to explain |
| 2026-10-13 | Default in-memory Spring Security user disabled | No generated password in logs; real users in step F |
| 2026-10-13 | **Simplification:** local MySQL, no Docker / Docker Compose | Easier to run for an assignment |
| 2026-10-13 | **Simplification:** no Testcontainers; automated tests avoid the database; full system checked with a demo checklist | No Docker; tests run anywhere |
| 2026-10-13 | **Simplification:** keep Flyway (works with no extra setup) | Reproducible tables; easy to show |
| 2026-10-13 | **Simplification:** packages by layer (`controller`, `service`, `repository`, `entity`, …) | Easiest structure to explain |
| 2026-10-13 | **Simplification:** `/api/...` paths without `/v1` | Single client; versioning unnecessary |
| 2026-10-13 | **Simplification:** one row per rule (no version history); alerts store a copy of rule text | Fewer tables; alerts still readable after edits |
| 2026-10-13 | **Simplification:** plain-Java rule validator instead of JSON Schema library | One less dependency; clearer error messages |
| 2026-10-13 | **Simplification:** session login with `SameSite=Strict` cookie; CSRF tokens disabled | No token handling in the frontend; adequate for local use |
| 2026-10-13 | **Simplification:** no audit-log tables, OpenAPI, Playwright, or CI | Out of scope for the assignment |
| 2026-10-13 | APIs open in `dev` during steps C–E; locked down in step F | Implementation order puts authentication last |
| 2026-10-13 | `spring-boot-maven-plugin` runs with the `dev` profile by default | Simplest run command: `./mvnw spring-boot:run` |
| 2026-10-13 | Step C "knowledge base" = drugs, diseases, lab tests (per the step C requirements); clinical rules move to step D | Rules can then reference these records |
| 2026-10-13 | Patient = `id`, full name, date of birth, gender, optional phone/email/address; replaces the earlier MRN/first-/last-name/sex proposal | Step C requirements |
| 2026-10-13 | Patient ID is the auto-generated numeric `id` (no separate MRN) | Simplest; can be added later if the diagrams require it |
| 2026-10-13 | Lab-test reference range stored as numeric low/high + unit | Lets step D rules compare results with the range; qualitative ranges ("negative") are out of scope |
| 2026-10-13 | Drugs unique by name + dosage; diseases by ICD-10 code; lab tests by name | Allows several strengths of one drug |
| 2026-10-13 | Sample data in a Flyway migration (`V3`), clearly labelled illustrative | Demo has data immediately; values flagged for local verification |
| 2026-10-13 | `cdss.security.open-api` flag (dev profile only) instead of a profile check in code | Explicit, testable, removed in step F |
| 2026-10-13 | Clinical records = vital signs (one row per set, fixed units), lab results (→ lab tests) and conditions (→ diseases); no medications or allergies yet | Covers the step D requirements and the sample rules; keeps the model small |
| 2026-10-13 | Rule format: `ruleId`, `name`, `description`, `severity`, `priority`, `enabled`, `condition`, `recommendedActions`; condition stored in a MySQL JSON column | Matches the step D requirements; DB checks the JSON is well-formed |
| 2026-10-13 | Three-valued evaluation (TRUE/FALSE/UNKNOWN); UNKNOWN reported as "insufficient data" | Missing data is never treated as normal |
| 2026-10-13 | Observations older than 24 h (vitals) / 72 h (labs) are ignored; limits in `application.yml` | Rules must not rely on stale data; configurable |
| 2026-10-13 | Implausible vital signs rejected at entry (400); lab units must match, no conversion | Invalid observations never reach the rules |
| 2026-10-13 | Rules re-validated at every evaluation; invalid ones reported, others still run | A renamed lab test cannot silently break a rule |
| 2026-10-13 | Decisions stored with copies of rule details; not-matched rules only counted | History stays readable after rule edits; small tables |
| 2026-10-13 | Alert acknowledgement (earlier plan) not implemented | Not in the step D requirements; can be added later |
| 2026-10-13 | Opt-in MySQL integration tests (`CDSS_MYSQL_IT=true`), rolled back after each test | Real-database checks without slowing or breaking the default test run |
| 2026-10-14 | **Deviation:** a new React frontend was built in step E instead of integrating an existing one | The expected existing frontend was not supplied; approved by the repository owner |
| 2026-10-14 | Frontend: React + TS + Vite + Tailwind + React Router only; plain `useState`/`fetch` | Simple to explain; no extra state or UI libraries |
| 2026-10-14 | Vite dev-server proxy for `/api` instead of backend CORS | Same origin in development; no backend change needed |

### Superseded (earlier plan, kept for the record)
Docker Compose MySQL; Testcontainers integration tests; feature-based
packages; `/api/v1` prefix; rule version tables; JSON Schema validation;
CSRF cookie-to-header tokens; audit log; Playwright end-to-end tests; CI.

### Deviations from project diagrams/requirements
The diagrams and requirements documents are still not in the repository, so
no comparison has been possible. The step C and D decisions above follow the
written step requirements. Add each difference here once the documents
are reviewed.
