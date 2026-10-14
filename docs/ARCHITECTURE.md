# Architecture

Educational CDSS prototype (undergraduate software engineering assignment) —
synthetic data only — not for clinical use.

> **Status.** Steps A–H implemented (see [`PROJECT_PLAN.md`](PROJECT_PLAN.md)):
> backend on local MySQL with Flyway migrations V1–V12 applied, React
> frontend, session login with `ADMIN`/`CLINICIAN` roles, patients and
> clinical records, knowledge base, JSON clinical rules with a plain-Java rule
> engine, decisions with stored history, medications, allergies, the
> medication safety check (illustrative sample interaction data only),
> follow-ups and user management.
>
> **Clinical safety:** all data is synthetic and every rule is an
> illustrative example — not clinically validated. A rule match is a prompt
> for clinician review, never a diagnosis. See §5.7.
>
> **Not yet reconciled:** the project's diagrams and requirements document
> are not in the repository. When they are added, align this document with
> them and record any deviation in §10.

## 1. System overview

```
┌─────────────────────────────┐   HTTP + JSON, /api/...   ┌───────────────────────────────┐
│ Browser                     │   (session cookie +       │ Spring Boot backend (:8080)   │
│ React + TypeScript          │──── CSRF header) ────────▶│                               │
│ + Tailwind app              │◀──────────────────────────│  controller  (REST endpoints) │
│ (frontend dev server)       │                           │  service     (business logic) │
└─────────────────────────────┘                           │  ruleengine  (plain Java)     │
   dev server proxies /api → 127.0.0.1:8080               │  repository  (Spring Data JPA)│
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
  - The Vite dev server (bound to `localhost`) proxies `/api` to
    `http://127.0.0.1:8080`, where the backend's `dev` profile listens, so the
    browser sees a single origin. No CORS is configured in the backend, so
    the `SameSite=Strict` session cookie is sent normally.
  - The client keeps the CSRF token (from `GET /api/auth/csrf`) in memory and
    sends it on every POST, PUT and DELETE; on a CSRF rejection it fetches a
    new token and retries once. A 401 sends the user to the login page.
  - No secrets are present in, or needed by, the frontend.
- **Screens** — two workspaces, chosen by the role the backend reports
  (`GET /api/auth/me`):
  - login page (everything else needs a login);
  - **clinician:** clinical overview dashboard (patient and follow-up counts,
    quick actions, due follow-ups, recently updated patients); patients;
    clinical decisions; follow-ups; read-only drugs, diseases, lab tests and
    plain-English decision rules (`/reference/rules`);
  - **administrator:** admin dashboard (live health check, user, rule and
    catalogue figures); user management (`/admin/users`); clinical rules
    (builder, JSON editor, validation, dry run, activation); catalogue
    editing; plus the clinical workflows;
  - patient detail with tabs: request decision, vital signs, lab results,
    conditions, medications, allergies, follow-ups, decision history;
  - My profile (username, role, permissions) from the account menu.
  - Admin pages show an access-denied message to clinicians
    (`RequireRole`); the backend answers 403 regardless.
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
  | `controller` | `@RestController` classes | `HealthController`, `PatientController`, `DrugController`, `DiseaseController`, `LabTestController`, `ClinicalRecordController`, `ClinicalRuleController`, `DecisionController`, `AuthController`, `MedicationController`, `AllergyController`, `DrugInteractionController`, `FollowUpController`, `UserAdminController` |
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
  repository-root `.env` file, so a student only edits one file, and reads
  the demo-user passwords (`CDSS_DEMO_ADMIN_PASSWORD`,
  `CDSS_DEMO_CLINICIAN_PASSWORD`) from it.
- **Errors:** `GlobalExceptionHandler` (`@RestControllerAdvice`) returns JSON
  `ProblemDetail` errors — `{type, title, status, detail, instance}` plus an
  `errors` object (field or rule path → message) for validation failures.
  Statuses: 400 invalid body/parameter/rule, 401 not logged in or failed
  login, 403 wrong role or missing/invalid CSRF token (`"code": "CSRF"`),
  404 missing record or URL, 409 duplicate or record still in use, 500
  generic message (details logged server-side only). 401/403 from the
  security filters are written in the same format by
  `SecurityErrorResponses`.

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
| `V7__users.sql` | F | `users` (no rows: demo users are created at startup from `.env`) |
| `V8__patient_medications_and_allergies.sql` | G | `patient_medications`, `patient_allergies`; two nullable "no known allergies" columns on `patients` |
| `V9__drug_interactions.sql` | G | `drug_interactions` + 2 **illustrative sample** rows (not clinically reviewed) |
| `V10__follow_ups.sql` | G | `follow_ups` |
| `V11__rename_seed_patients.sql` | H | Data only: renames the 4 synthetic seed patients (placeholder names → plausible fictional names) and their seed condition notes, only where every seeded value is unchanged; IDs and clinical data untouched |
| `V12__drug_interaction_source_details.sql` | H | Four nullable columns on `drug_interactions` (`mechanism`, `recommended_action`, `source_version`, `last_reviewed_on`); no data: the sample rows stay NULL |

V1–V12 are applied to the local database. Apart from V11's data-only rename of
untouched seed patients, they are additive: new tables, nullable columns, and
two sample interaction rows; no row is deleted.

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
| `patients` (V8 additions) | `no_known_allergies_at`, `no_known_allergies_by` (both NULL = not confirmed) | — |
| `patient_medications` | `id`, `patient_id`, `drug_id`, `dose`, `dose_unit`, `route`, `frequency`, `start_date`, `end_date`, `status`, `notes`, `status_reason`, `status_changed_at/by`, `safety_warnings` (JSON), `safety_override_reason`, `recorded_by`, `created_at`, `updated_at` | FK → `patients` (CASCADE); FK → `drugs` (RESTRICT); CHECK status, route, dose > 0, end ≥ start |
| `patient_allergies` | `id`, `patient_id`, `drug_id` (DRUG only), `allergen`, `category`, `reaction`, `severity`, `status`, `recorded_date`, `notes`, `resolution_reason`, `resolved_at/by`, `recorded_by`, `created_at`, `updated_at` | FK → `patients` (CASCADE); FK → `drugs` (RESTRICT); CHECK category, severity, status; `drug_id` set exactly for DRUG |
| `drug_interactions` | `id`, `drug_name_a`, `drug_name_b`, `severity`, `description`, `source`, `data_classification`, `review_status`, `created_at`; nullable `mechanism`, `recommended_action`, `source_version`, `last_reviewed_on` (V12) | UNIQUE pair; CHECK a < b, severity, classification, review status |
| `follow_ups` | `id`, `patient_id`, `scheduled_at` (UTC), `reason`, `notes`, `status`, `reschedule_count`, `outcome_notes`, `status_changed_at/by`, `created_by`, `created_at`, `updated_at` | FK → `patients` (CASCADE); CHECK status |
| `users` | `id`, `username`, `password_hash` (BCrypt), `role`, `enabled`, `created_at` | UNIQUE `username` (case-insensitive via collation); CHECK `role` IN (`ADMIN`, `CLINICIAN`) |

- **Units** of vital signs are fixed: blood pressure mmHg, temperature °C,
  heart rate beats/min, respiratory rate breaths/min, oxygen saturation %.
  A lab result stores the unit of its lab test.
- **Deleting a patient** removes their records and decisions (CASCADE).
- **Deleting a lab test, disease or drug** that patient records still use
  is refused with 409.
- **Medications, allergies and follow-ups are never deleted** by the
  application: they change status (discontinued/completed, resolved,
  completed/cancelled) so history is kept. Like all clinical records they are
  removed only when their patient is deleted.
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
  symptoms, mental status, medications, allergies or trends. Medications and
  allergies are checked separately by the medication safety check (§5.8).
- Implausible vital signs are rejected when recorded (§7). Plausible but
  wrong entries cannot be detected.

### 5.8 Medication safety check (allergies and interactions)

Separate from the rule engine (`MedicationSafetyChecker`, plain Java, and
`MedicationSafetyService`). Before a medication is saved, the proposed drug is
compared with the patient's **own** records:

- **Allergies** (ACTIVE only): a DRUG allergy matches any knowledge-base drug
  with exactly the same name (every strength); a DRUG_CATEGORY allergy matches
  the exact knowledge-base category. Similar names are never treated as the
  same drug. A free-text OTHER allergy cannot be matched and is reported as
  UNCERTAIN. Food and environmental allergies are not compared with drugs.
- **Allergy history**: if no allergy is active and "no known allergies" was
  never confirmed, the result is UNCERTAIN ("not assessed"), never "no
  allergies".
- **Duplicate therapy**: another active medication with the same drug name.
- **Interactions**: pairs in `drug_interactions` (by drug name) between the
  proposed drug and each other active medication. **The dataset is two
  illustrative sample rows, not a drug-interaction database**; every result
  from it is labelled sample data. Finding nothing is reported as
  NO_MATCH_FOUND with the limits of the data, never as "safe".

- **Unavailable**: if the patient has other active medications but no
  interaction data is loaded, the interaction result is UNCERTAIN ("check
  unavailable"). A proposed drug that appears in no dataset row is called out
  as "not covered".

Each finding has a code, check type, result (MATCH_FOUND / NO_MATCH_FOUND /
UNCERTAIN), a title, a review priority (HIGH = matched, MEDIUM = could not be
checked, LOW = nothing matched; review order only, not clinical urgency),
severity (from the record or dataset row, if any), the medications and the
recorded allergens involved, an explanation, the mechanism and recommended
action, its source with data classification (PATIENT_RECORD /
ILLUSTRATIVE_SAMPLE / VERIFIED), source version, review date and review
status, and whether clinician review is required. Mechanism, recommended
action, version and review date for an interaction come **only** from the
dataset row (V12 columns) and are null when the row has none; the UI then
shows "Not provided by the source". Patient-record findings carry this
check's own procedural review prompt (e.g. "confirm the allergy history with
the patient"), never treatment advice. Findings are sorted by priority.

A finding that needs review has a code ending in a short SHA-256 fingerprint
of its content. An unchanged finding keeps its code, so a re-check does not
raise it again; a materially changed finding (e.g. allergy severity edited)
gets a new code and must be acknowledged again. The frontend re-runs an open
check after the medication list changes and when the save-time check differs,
keeps acknowledgements only for unchanged codes, and marks new or changed
findings. Custom clinical rules have no medication or allergy facts, so they
never affect this check, and the check does not depend on rule activation or
follow-ups. `POST …/medications` re-runs the check: every
finding that needs review must be listed in `acknowledgedWarnings` and a
reason given, otherwise the request is refused with **409** and the check
(no API request can skip it). The acknowledged warnings and reason are stored
with the medication. The check never adds, changes or stops a medication.

### 5.9 Rule management

Rules keep the same JSON format and evaluator. `POST /api/rules/{id}/activate`
validates the stored rule against the current facts first (400 with the
problems if invalid, so an invalid rule cannot be activated);
`POST /api/rules/{id}/deactivate` never validates, so even a rule that has
become invalid can be switched off. `POST /api/rules/dry-run` evaluates an
unsaved condition against one patient's current data with the real evaluator
and stores nothing. The frontend adds a condition builder (for a single
comparison or one ALL/ANY list; nested conditions are edited as JSON), a
plain-English preview, readable error locations, search and status filters.

## 6. Security

Sensible security for a local assignment — **not** production security.

- **Login *(implemented, step F)*:** `POST /api/auth/login` with JSON
  username/password (`AuthController`). Spring Security checks the BCrypt
  hash (`AuthenticationConfig`, `UserAccountService`) and the login is kept
  in a server-side **HTTP session** (cookie `JSESSIONID`, HttpOnly,
  `SameSite=Strict`, 30 minutes idle timeout). On login the session ID and
  the CSRF token are replaced (session-fixation protection). A wrong
  password, an unknown user and a disabled account all get the same 401
  message. `POST /api/auth/logout` (handled by Spring Security) ends the
  session and answers 204. `GET /api/auth/me` returns the current user. No
  JWTs or other token schemes.
- **Passwords:** BCrypt. Demo users `admin` and `clinician` are created at
  startup in the `dev` profile only, from `.env` values
  (`CDSS_DEMO_ADMIN_PASSWORD`, `CDSS_DEMO_CLINICIAN_PASSWORD`), and only if
  they don't exist yet; existing users are never changed, and a missing
  password or one outside 8–72 characters is refused with a warning. No
  passwords in source code, migrations or logs. See `docs/DEMO.md`.
- **Roles *(implemented, step F)*:** enforced on the server by URL rules in
  `SecurityConfig`; the frontend only hides buttons. Unknown paths outside
  `/api` are denied.

  | Action | CLINICIAN | ADMIN |
  |--------|:---------:|:-----:|
  | View/search patients | ✓ | ✓ |
  | Create/update patients | ✓ | ✓ |
  | Delete patients | | ✓ |
  | View knowledge base (drugs, diseases, lab tests) | ✓ | ✓ |
  | View rules in plain English (`GET /api/rules/summaries`, no JSON) | ✓ | ✓ |
  | View rule JSON definitions and facts (`GET /api/rules`, `/{id}`, `/facts`) | | ✓ |
  | Create/update/delete knowledge-base entries and rules | | ✓ |
  | User management (`/api/admin/**`) | | ✓ |
  | Record and delete vital signs, lab results, conditions | ✓ | ✓ |
  | Run decisions, view decision history | ✓ | ✓ |
  | Medications (add with safety check, edit, discontinue, complete), allergies, follow-ups | ✓ | ✓ |
  | Activate/deactivate rules, rule dry runs | | ✓ |

- **CSRF *(implemented, step F)*:** Spring Security CSRF protection is on for
  every POST, PUT and DELETE, including login and logout. The token is kept
  in the HTTP session (not in a cookie JavaScript can read); the frontend
  fetches it from `GET /api/auth/csrf` and sends it in the `X-CSRF-TOKEN`
  header. Spring masks it differently on each response. `SameSite=Strict`
  remains as a second layer; it alone is not enough because other local
  apps on different `localhost` ports count as the same site.
- **Public endpoints:** only `GET /api/health` (overall status, database
  UP/DOWN, timestamp), `GET /api/auth/csrf` and `POST /api/auth/login`.
- **Network binding *(implemented)*:** the `dev` profile sets
  `server.address: 127.0.0.1`, so over plain HTTP only this computer can
  connect. The base `application.yml` sets no address, so any other
  configuration chooses its own.
- **HTTP vs HTTPS:** locally the session cookie is not `Secure`, because
  browsers do not reliably keep `Secure` cookies received over HTTP. A future
  HTTPS deployment (none exists; the project is not production-ready) would
  set `server.servlet.session.cookie.secure=true` and, behind a
  TLS-terminating proxy, `server.forward-headers-strategy=native`.
  `HttpOnly`, `SameSite=Strict` and CSRF stay unchanged; Spring Security
  sends HSTS on HTTPS requests by itself.
- **Ending sessions:** login registers the session in Spring Security's
  `SessionRegistry`. Deactivating a user or changing their role expires their
  sessions; their next request answers 401 ("Your session has ended because
  your account was changed by an administrator"). Sessions are in memory, so
  a backend restart also ends them.
- **User management:** accounts are created by administrators with an
  explicit role and an initial password (BCrypt), never deleted, only
  deactivated. You cannot deactivate yourself or change your own role, and the
  last active administrator cannot be deactivated or demoted. No password,
  hash or session ID is ever returned.
- **Known limitations (unresolved):** login attempts are **not
  rate-limited** and there is no account lockout, so passwords can be
  guessed as fast as BCrypt allows (the loopback binding limits this to
  programs on the same computer, but does not fix it); no password reset or
  user management; no audit trail of which user did what.
- Input validation on all DTOs; rule validation before saving and before
  each evaluation; JPA only (no
  string-built SQL); generic error messages to clients.

## 7. API

All endpoints are under `/api`, JSON in and out. Lists return plain JSON
arrays (the data set is small, so no pagination).
"Login" = any logged-in user (`ADMIN` or `CLINICIAN`); "ADMIN" = admins
only. Without a login every endpoint except the public ones answers 401; a
clinician calling an ADMIN endpoint gets 403. Every POST, PUT and DELETE
also needs the CSRF token (403 with `"code": "CSRF"` otherwise).

### Implemented

| Method | Path | Access | Success | Errors |
|--------|------|--------|---------|--------|
| GET | `/api/health` | public | 200 (503 if database down) | — |
| GET | `/api/auth/csrf` | public | 200 `{headerName, token}` for this session | — |
| POST | `/api/auth/login` | public (CSRF token) | 200 `{username, role}`; new session ID and CSRF token | 400, 401, 403 CSRF |
| GET | `/api/auth/me` | login | 200 `{username, role}` | 401 |
| POST | `/api/auth/logout` | public (CSRF token) | 204; session ended | 403 CSRF |
| GET | `/api/patients?search=` | login | 200 list (sorted by name; `search` matches part of the name) | — |
| GET | `/api/patients/{id}` | login | 200 | 400 bad id, 404 |
| POST | `/api/patients` | login | 201 + `Location` | 400 |
| PUT | `/api/patients/{id}` | login | 200 | 400, 404 |
| DELETE | `/api/patients/{id}` | ADMIN | 204 | 404 |
| GET / POST | `/api/drugs` | login / ADMIN | 200 / 201 | 400, 409 |
| GET / PUT / DELETE | `/api/drugs/{id}` | login / ADMIN | 200 / 200 / 204 | 400, 404, 409 |
| GET / POST | `/api/diseases` | login / ADMIN | 200 / 201 | 400, 409 |
| GET / PUT / DELETE | `/api/diseases/{id}` | login / ADMIN | 200 / 200 / 204 | 400, 404, 409 |
| GET / POST | `/api/lab-tests` | login / ADMIN | 200 / 201 | 400, 409 |
| GET / PUT / DELETE | `/api/lab-tests/{id}` | login / ADMIN | 200 / 200 / 204 | 400, 404, 409 |
| GET / POST | `/api/patients/{id}/vital-signs` | login | 200 (newest first) / 201 | 400, 404 |
| DELETE | `/api/patients/{id}/vital-signs/{recordId}` | login | 204 | 404 |
| GET / POST | `/api/patients/{id}/lab-results` | login | 200 (newest first) / 201 | 400, 404 |
| DELETE | `/api/patients/{id}/lab-results/{recordId}` | login | 204 | 404 |
| GET / POST | `/api/patients/{id}/conditions` | login | 200 / 201 | 400, 404, 409 |
| DELETE | `/api/patients/{id}/conditions/{recordId}` | login | 204 | 404 |
| GET | `/api/rules/summaries` | login | 200 plain-English rules (no JSON) | — |
| GET | `/api/rules` | ADMIN | 200 (priority order; includes `valid` and `validationErrors`) | — |
| GET | `/api/rules/{id}` | ADMIN | 200 | 400, 404 |
| GET | `/api/rules/facts` | ADMIN | 200: facts and allowed operators | — |
| GET / POST | `/api/admin/users` | ADMIN | 200 list (no password data) / 201 | 400, 409 duplicate |
| POST | `/api/admin/users/{id}/activate` / `deactivate` | ADMIN | 200 (deactivation ends the user's sessions) | 404, 409 self or last active admin |
| PUT | `/api/admin/users/{id}/role` | ADMIN | 200 (ends the user's sessions) | 400, 404, 409 self or last active admin |
| POST | `/api/rules/validate` | ADMIN | 200 `{valid, errors}` (nothing saved) | 400 for missing fields |
| POST | `/api/rules` | ADMIN | 201 | 400 (fields or invalid rule), 409 |
| PUT / DELETE | `/api/rules/{id}` | ADMIN | 200 / 204 | 400, 404, 409 |
| POST | `/api/patients/{id}/decisions` | login | 201 + `Location`: the stored decision | 400, 404 |
| GET | `/api/patients/{id}/decisions` | login | 200: history, newest first | 404 |
| GET | `/api/patients/{id}/decisions/{decisionId}` | login | 200 | 404 |
| GET | `/api/patients/{id}/medications` | login | 200 (newest start first, all statuses) | 404 |
| GET | `/api/patients/{id}/medications/{medicationId}` | login | 200 | 404 |
| POST | `/api/patients/{id}/medications/safety-check` | login | 200 structured safety check (nothing saved) | 400, 404 |
| POST | `/api/patients/{id}/medications` | login | 201 (check re-run; acknowledged warnings stored) | 400, 404, 409 duplicate active drug or unacknowledged warnings |
| PUT | `/api/patients/{id}/medications/{medicationId}` | login | 200 (ACTIVE only; drug cannot change) | 400, 404, 409 |
| POST | `/api/patients/{id}/medications/{medicationId}/discontinue` / `complete` | login | 200 (kept as history) | 400, 404, 409 |
| GET | `/api/patients/{id}/allergies` | login | 200 `{assessment, …, allergies}` | 404 |
| POST | `/api/patients/{id}/allergies` | login | 201 (clears "no known allergies") | 400, 404, 409 duplicate |
| PUT | `/api/patients/{id}/allergies/{allergyId}` | login | 200 (ACTIVE only) | 400, 404, 409 |
| POST | `/api/patients/{id}/allergies/{allergyId}/resolve` | login | 200 (kept as history) | 400, 404, 409 |
| POST | `/api/patients/{id}/allergies/no-known-allergies` | login | 200 | 404, 409 while an allergy is active |
| GET | `/api/drug-interactions` | login | 200 the (illustrative) interaction dataset, with source details where recorded (null otherwise) | — |
| GET | `/api/follow-ups` | login | 200 all, earliest first, with calculated `timing` | — |
| GET / POST | `/api/patients/{id}/follow-ups` | login | 200 / 201 | 400 (time not in the future), 404 |
| POST | `/api/patients/{id}/follow-ups/{followUpId}/reschedule` / `complete` / `cancel` | login | 200 (SCHEDULED only; cancel needs a reason) | 400, 404, 409 |
| POST | `/api/rules/{id}/activate` / `deactivate` | ADMIN | 200 | 400 invalid rule (activate), 404 |
| POST | `/api/rules/dry-run` | ADMIN | 200 `{valid, errors, result, reasons}` (nothing saved) | 400, 404 |

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
| Medication | `drugId`*, `dose`* (> 0, 2 decimals), `doseUnit`*, `route`* (`ORAL`, `INTRAVENOUS`, …), `frequency`*, `startDate`*, `endDate` (≥ start; not in the past while active), `notes`, `acknowledgedWarnings` (finding codes), `overrideReason` (required when warnings are acknowledged) |
| Allergy | `category`* (`DRUG` + `drugId`*, `DRUG_CATEGORY` + knowledge-base category as `allergen`*, or `FOOD`/`ENVIRONMENTAL`/`OTHER` + free-text `allergen`*), `reaction`, `severity`* (`MILD`/`MODERATE`/`SEVERE`/`UNKNOWN`), `recordedDate`* (not in the future), `notes` |
| Follow-up | `scheduledAt`* (ISO-8601 instant in the future), `reason`* (≤200), `notes` (≤500) |

Text is trimmed; empty optional fields are stored as `null`. Updates (`PUT`)
replace all fields. Clinical records are added and deleted (no update); to
correct one, delete it and add it again.

### Planned

No further endpoints are planned. Any new feature is added to
`docs/PROJECT_PLAN.md` first, after approval.

## 8. Local development

| Piece | How it runs |
|-------|-------------|
| MySQL | Installed locally (e.g. `brew install mysql` + `brew services start mysql` on macOS, MySQL Installer on Windows). One-time SQL creates the `cdss` database and `cdss_app` user — see `backend/README.md`. |
| Backend | `cd backend && ./mvnw spring-boot:run` (uses the `dev` profile, reads `../.env`, listens on `127.0.0.1:8080` only) |
| Frontend | `cd frontend && npm install && npm run dev` → http://localhost:5173 (proxies `/api` to the backend at `127.0.0.1:8080`) |

## 9. Testing strategy

| Level | Tooling | Covers | Needs MySQL? |
|-------|---------|--------|:------------:|
| Rule engine | JUnit 5 (incl. parameterized) | Parser (valid, invalid, limits), evaluator (each operator at boundaries, AND/OR/NOT, missing data), every sample rule read from `V6__sample_rules.sql` | No |
| Services | JUnit 5 + Mockito | Patients, knowledge base, clinical records, rules (activate/deactivate), facts collection, decisions (incl. dry run), medications, allergies, follow-ups (timing boundaries), the safety checker (allergy/category/free-text/not-assessed/interaction/no-match cases), the V9 sample data labels | No |
| Controllers | `@WebMvcTest` + Spring Security Test | Status codes, validation errors, 404/409, response shapes; every endpoint's 401/403/allowed per role; CSRF; login, `/me`, logout with real sessions and BCrypt | No |
| Integration | `@SpringBootTest` (`MySqlIntegrationTests`) | Real MySQL: migrations, JSON columns, constraints, cascades, full decision flow, login with a stored BCrypt user, medications with the safety check and acknowledgement, "no known allergies", follow-ups. Uses the **development** database through the `dev` profile (no separate test database), so Flyway applies pending migrations to it; each test is rolled back and demo users are not created. | **Yes**, opt-in: `CDSS_MYSQL_IT=true ./mvnw test` |
| Frontend | Vitest + React Testing Library, `fetch` mocked (`cd frontend && npm test`) | API client errors, CSRF header and retry, 401 handling, login/logout flow, role-based buttons, decision display wording, patient list/form, rules page (validation, builder, plain English, dry run, activation, filters), medications (safety check, acknowledgement, history), allergies (not-assessed wording, NKA), follow-ups page | No |
| Whole system | Manual demo checklist (`docs/DEMO.md`) | Browser flows, roles, CSRF and cookies end to end | Yes |

Last run (2026-10-14): backend 499 tests, 0 failures, 9 skipped (the MySQL
integration tests, not run); frontend 75 tests pass; production build
succeeds. The owner confirmed the main workflows in the browser; the full
manual checklist has not been formally ticked.

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
| 2026-10-13 | Default in-memory Spring Security user disabled | No generated password in logs; real users are in the `users` table (step F) |
| 2026-10-13 | **Simplification:** local MySQL, no Docker / Docker Compose | Easier to run for an assignment |
| 2026-10-13 | **Simplification:** no Testcontainers; automated tests avoid the database; full system checked with a demo checklist | No Docker; tests run anywhere |
| 2026-10-13 | **Simplification:** keep Flyway (works with no extra setup) | Reproducible tables; easy to show |
| 2026-10-13 | **Simplification:** packages by layer (`controller`, `service`, `repository`, `entity`, …) | Easiest structure to explain |
| 2026-10-13 | **Simplification:** `/api/...` paths without `/v1` | Single client; versioning unnecessary |
| 2026-10-13 | **Simplification:** one row per rule (no version history); alerts store a copy of rule text | Fewer tables; alerts still readable after edits |
| 2026-10-13 | **Simplification:** plain-Java rule validator instead of JSON Schema library | One less dependency; clearer error messages |
| 2026-10-13 | **Simplification:** no audit-log tables, OpenAPI, Playwright, or CI | Out of scope for the assignment |
| 2026-10-13 | APIs open in `dev` during steps C–E; locked down in step F (done 2026-10-14) | Implementation order puts authentication last |
| 2026-10-13 | `spring-boot-maven-plugin` runs with the `dev` profile by default | Simplest run command: `./mvnw spring-boot:run` |
| 2026-10-13 | Step C "knowledge base" = drugs, diseases, lab tests (per the step C requirements); clinical rules move to step D | Rules can then reference these records |
| 2026-10-13 | Patient = `id`, full name, date of birth, gender, optional phone/email/address; replaces the earlier MRN/first-/last-name/sex proposal | Step C requirements |
| 2026-10-13 | Patient ID is the auto-generated numeric `id` (no separate MRN) | Simplest; can be added later if the diagrams require it |
| 2026-10-13 | Lab-test reference range stored as numeric low/high + unit | Lets step D rules compare results with the range; qualitative ranges ("negative") are out of scope |
| 2026-10-13 | Drugs unique by name + dosage; diseases by ICD-10 code; lab tests by name | Allows several strengths of one drug |
| 2026-10-13 | Sample data in a Flyway migration (`V3`), clearly labelled illustrative | Demo has data immediately; values flagged for local verification |
| 2026-10-13 | `cdss.security.open-api` flag (dev profile only) instead of a profile check in code; **removed in step F** | Explicit, testable |
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
| 2026-10-14 | Session login (server-side HTTP session, `JSESSIONID` HttpOnly + `SameSite=Strict`), BCrypt, roles `ADMIN`/`CLINICIAN` enforced by URL rules in `SecurityConfig` | Step F requirements; simple to explain; no tokens |
| 2026-10-14 | **Changed:** CSRF protection **enabled** (token stored in the session, fetched from `GET /api/auth/csrf`, sent as `X-CSRF-TOKEN`); replaces "CSRF disabled, rely on `SameSite=Strict`" | `SameSite` alone does not cover other apps on `localhost` ports (same site); approved by the repository owner |
| 2026-10-14 | Custom JSON login endpoint (`AuthController`) instead of Spring's form login | JSON API; explicit session-ID and CSRF-token rotation that a student can read |
| 2026-10-14 | Demo users created at startup (dev profile) from `.env`, only if missing; never overwritten; no users in migrations | No credentials in the repository; no silent password resets |
| 2026-10-14 | Clinicians may delete individual vital signs, lab results and conditions | Part of clinical record-keeping; approved by the repository owner |
| 2026-10-14 | Entity named `UserAccount` (table `users`) | Avoids confusion with Spring Security's `User` class |
| 2026-10-14 | `dev` profile binds the backend to `127.0.0.1`; Vite proxy targets `127.0.0.1:8080` and the dev server stays on `localhost` | Plain-HTTP development must not be reachable from the network; `localhost` can resolve to IPv6 `::1`, where the backend does not listen |
| 2026-10-14 | Session cookie not `Secure` in any current configuration; HTTPS settings documented only | No HTTPS deployment exists; `Secure` would break local HTTP login in some browsers |
| 2026-10-14 | Login rate limiting **not implemented** (documented limitation) | Deferred by the repository owner |
| 2026-10-14 | Medications reuse the drug knowledge base (`drug_id`); never deleted, only discontinued/completed; drug cannot be changed after adding (record a new one) | Keeps history and the safety check valid |
| 2026-10-14 | Allergy history has three states; "not assessed" is never shown or treated as "no allergies"; NKA must be confirmed explicitly and is cleared when an allergy is recorded | Missing data must never be treated as normal |
| 2026-10-14 | Safety check matches drugs by exact name (all strengths) or exact knowledge-base category; free-text allergies are UNCERTAIN | No reliable alias data; similar names are not assumed equivalent |
| 2026-10-14 | Interaction data: two **illustrative sample** rows keyed by drug name, labelled in every result; no licensed source | No verified source available; must not be presented as a real database |
| 2026-10-14 | Warnings needing review must be acknowledged (by code) with a reason, enforced by the backend (409); acknowledgements stored on the medication | Clinician decides; the API cannot skip the check |
| 2026-10-14 | Rule activation re-validates; deactivation never does; dry run with the real evaluator; builder only for flat conditions | Invalid rules cannot be activated, broken rules can still be switched off; JSON format unchanged |
| 2026-10-14 | Separate clinician and administrator workspaces; rule JSON, facts and user management are ADMIN-only on the backend; clinicians get `GET /api/rules/summaries` (plain English generated by `RuleDescriber`) | Clinicians need to understand rules, not edit them; enforced server-side |
| 2026-10-14 | User management without a new migration (the `users` table already had `enabled`, `role`, `created_at`); no deletes; self/last-admin guards; sessions expired via `SessionRegistry` | Safe, immediate effect; no fabricated profile or activity data |
| 2026-10-14 | Branding "Clinical Decision Support System — Patient Insights & Clinical Decision Support"; no prototype banner or footer in the application chrome; the disclaimer stays on the login page, with every decision result and on illustrative interaction results (CLAUDE.md updated) | Owner asked to remove repetitive prototype branding while keeping clinical safety notices |
| 2026-10-14 | Health indicator and system health shown to administrators only (endpoint stays public; it exposes only UP/DOWN and a timestamp) | Clinicians do not need system status |
| 2026-10-14 | Safety findings carry title, review priority, allergens, mechanism, recommended action and source version/review date; V12 adds nullable source-detail columns that stay NULL for the sample rows | Doctor-facing feedback must show what the source says and say plainly when it says nothing; nothing is invented |
| 2026-10-14 | Acknowledgement codes include a content fingerprint | Unchanged findings are not re-raised, but a materially changed finding cannot ride on an old acknowledgement |
| 2026-10-14 | Follow-up timing (OVERDUE / UPCOMING within `cdss.follow-ups.upcoming-window`, default 7 days / LATER) calculated on read from UTC instants; completed/cancelled never overdue; no background jobs or notifications | Simple and testable; times shown in the browser's time zone |

### Superseded (earlier plan, kept for the record)
Docker Compose MySQL; Testcontainers integration tests; feature-based
packages; `/api/v1` prefix; rule version tables; JSON Schema validation;
CSRF cookie-to-header tokens (replaced by session-stored tokens fetched from
`/api/auth/csrf`); "CSRF disabled, `SameSite=Strict` only" (replaced in step
F); audit log; Playwright end-to-end tests; CI.

### Deviations from project diagrams/requirements
The diagrams and requirements documents are still not in the repository, so
no comparison has been possible. The step C and D decisions above follow the
written step requirements. Add each difference here once the documents
are reviewed.
