# backend/

Spring Boot backend for the educational CDSS prototype
(synthetic data only — not for clinical use).

**Status:** step D complete (see
[`../docs/PROJECT_PLAN.md`](../docs/PROJECT_PLAN.md)).
Implemented: local MySQL connection, Flyway migrations V1–V6, `GET /api/health`,
patient CRUD, knowledge base (drugs, diseases, lab tests), patient clinical
records (vital signs, lab results, conditions), JSON clinical rules with a
plain-Java rule engine, clinical decisions with stored history, JSON error
responses.
**Not implemented yet:** login/users (step F).

> **Educational prototype.** The sample rules are illustrative and not
> clinically validated. A matched rule is a prompt for review, not a
> diagnosis. See the safety notes in
> [`../docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md) §5.7.

> **No login yet.** With the `dev` profile (the default for
> `./mvnw spring-boot:run`) every `/api` endpoint is open, and the backend logs
> a warning saying so. This is for local development only, until step F.

## Stack

Java 21 · Spring Boot 4.1.1 · Spring Web MVC · Spring Data JPA (Hibernate) ·
Spring Security · Bean Validation · Flyway · MySQL 8 (installed locally) ·
Maven Wrapper · JUnit 5 · Mockito.

**No Docker is needed.**

## 1. Prerequisites (one-time)

| Tool | macOS (Homebrew) | Windows |
|------|------------------|---------|
| JDK 21 | `brew install --cask temurin@21` | Install "Temurin 21 (LTS)" from adoptium.net |
| MySQL 8 | `brew install mysql@8.4` then `brew services start mysql@8.4` | MySQL Installer 8.4 (Server + Workbench); keep the default port 3306 |

Maven does not need to be installed: `./mvnw` (or `mvnw.cmd` on Windows)
downloads it automatically.

Check: `java -version` shows 21, and MySQL is running on port 3306.

> Homebrew's `mysql@8.4` is "keg-only". To use the `mysql` command, run
> `echo 'export PATH="$(brew --prefix mysql@8.4)/bin:$PATH"' >> ~/.zshrc`
> and open a new terminal.

## 2. Create the database and user (one-time)

First create `.env` (section 3) and pick the password there. Then open a
MySQL prompt as root (`mysql -u root`, or `mysql -u root -p` if you set a root
password, or a query tab in MySQL Workbench) and run, using **the same
password as `CDSS_DB_PASSWORD` in `.env`**:

```sql
CREATE DATABASE cdss CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'cdss_app'@'localhost' IDENTIFIED BY 'same-password-as-in-.env';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
  ON cdss.* TO 'cdss_app'@'localhost';
```

The application user only gets what the app and Flyway need on the `cdss`
database (read/write data; create and alter tables, indexes and foreign
keys). It has no rights on other databases, and no `DROP`. The root account
is only used for this one-time setup, never by the application.

The tables themselves are created by the backend (Flyway) — don't create them
by hand.

Tip: a random password can be generated with `openssl rand -hex 24`.

## 3. Configure

From the repository root:

```bash
cp .env.example .env
```

Edit `.env` and set `CDSS_DB_PASSWORD` to a password of your choice (the
same one is then used in the `CREATE USER` statement above).
`.env` is git-ignored, so your password is never committed.

| Variable | Default | Meaning |
|----------|---------|---------|
| `CDSS_DB_HOST` | `localhost` | MySQL host |
| `CDSS_DB_PORT` | `3306` | MySQL port |
| `CDSS_DB_NAME` | `cdss` | Database name |
| `CDSS_DB_USERNAME` | *(required)* | MySQL user (`cdss_app`) |
| `CDSS_DB_PASSWORD` | *(required)* | That user's password |

The `dev` profile reads `../.env` automatically, and `./mvnw spring-boot:run`
uses the `dev` profile by default (set in `pom.xml`). Real environment
variables, if set, take precedence.

## 4. Run

```bash
cd backend
./mvnw spring-boot:run
```

(Windows: `mvnw.cmd spring-boot:run`)

On startup Flyway applies the SQL files in `src/main/resources/db/migration`
and Hibernate checks the tables. The backend stops with an error if it cannot
reach MySQL — check MySQL is running and `.env` is correct.

Check it in another terminal (or a browser):

```bash
curl -i http://localhost:8080/api/health
```

Stop the backend with `Ctrl+C`.

### Health endpoint

`GET /api/health` (public):

```json
{
  "status": "UP",
  "components": { "application": "UP", "database": "UP" },
  "timestamp": "2026-10-13T16:30:00Z"
}
```

| Situation | HTTP | `status` | `components.database` |
|-----------|------|----------|-----------------------|
| Database reachable | 200 | `UP` | `UP` |
| Database lost while running | 503 | `DEGRADED` | `DOWN` |

Without the `dev` profile, every other path returns `401 Unauthorized`.

### Troubleshooting

| Error | Fix |
|-------|-----|
| `Communications link failure` | MySQL isn't running or the host/port is wrong (`brew services list`) |
| `Access denied for user 'cdss_app'@'localhost'` | Password in `.env` doesn't match; or the user/grant SQL wasn't run |
| `Unknown database 'cdss'` | Run the `CREATE DATABASE` statement |
| `Public Key Retrieval is not allowed` | Log in once with `mysql -u cdss_app -p` (this caches the key), then restart the backend |
| An error that mentions `${CDSS_DB_USERNAME}` or `${CDSS_DB_PASSWORD}` literally | `.env` is missing from the repository root (copy `.env.example`), or the app was started without the `dev` profile (e.g. `java -jar` — add `--spring.profiles.active=dev`) |
| Port 8080 already in use | Stop the other process, or add `--server.port=8081` via `-Dspring-boot.run.arguments=--server.port=8081` |

## 5. API

All endpoints use JSON. Errors use one format (RFC 9457 Problem Details):

```json
{ "type": "about:blank", "title": "Bad Request", "status": 400,
  "detail": "Validation failed", "instance": "/api/patients",
  "errors": { "fullName": "must not be blank", "email": "must be a well-formed email address" } }
```

| Status | When |
|--------|------|
| 200 OK | Read or update succeeded |
| 201 Created | Record created; `Location` header has its URL |
| 204 No Content | Record deleted |
| 400 Bad Request | Invalid fields (`errors` lists them), malformed JSON, or a non-numeric ID |
| 404 Not Found | No record with that ID, or unknown URL |
| 409 Conflict | Duplicate drug (name + dosage), disease (ICD-10 code) or lab test (name) |
| 500 | Unexpected error; generic message only, details in the server log |

### Patients — `/api/patients`

| Method | Path | Result |
|--------|------|--------|
| GET | `/api/patients` | All patients, sorted by name |
| GET | `/api/patients?search=ale` | Patients whose name contains "ale" |
| GET | `/api/patients/{id}` | One patient |
| POST | `/api/patients` | Create → 201 |
| PUT | `/api/patients/{id}` | Replace all fields → 200 |
| DELETE | `/api/patients/{id}` | Delete → 204 |

Fields: `fullName` (required), `dateOfBirth` (required, `YYYY-MM-DD`, not in
the future), `gender` (required: `MALE`, `FEMALE`, `OTHER`, `UNKNOWN`),
`phone`, `email`, `address` (optional). Responses also include `id`,
`createdAt`, `updatedAt`.

```bash
curl -i -X POST http://localhost:8080/api/patients \
  -H 'Content-Type: application/json' \
  -d '{"fullName":"Sam Sample","dateOfBirth":"1985-03-14","gender":"FEMALE",
       "phone":"+1 555 0150","email":"sam.sample@example.com","address":"5 Demo Avenue"}'

curl http://localhost:8080/api/patients/1

curl -i -X PUT http://localhost:8080/api/patients/1 \
  -H 'Content-Type: application/json' \
  -d '{"fullName":"Alex Example","dateOfBirth":"1958-04-12","gender":"MALE","phone":"+1 555 0111"}'

curl -i -X DELETE http://localhost:8080/api/patients/1
```

### Knowledge base — `/api/drugs`, `/api/diseases`, `/api/lab-tests`

Each supports `GET` (list), `GET /{id}`, `POST`, `PUT /{id}`, `DELETE /{id}`.

| Resource | Fields (* = required) | Must be unique |
|----------|-----------------------|----------------|
| Drug | `name`*, `dosage`*, `category`*, `notes` | name + dosage |
| Disease | `name`*, `icd10Code`* (e.g. `I10`, `E11.9`; saved in upper case) | ICD-10 code |
| Lab test | `name`*, `referenceRangeLow`*, `referenceRangeHigh`* (numbers, low ≤ high), `unit`*, `notes` | name |

Uniqueness ignores upper/lower case.

```bash
curl -i -X POST http://localhost:8080/api/drugs \
  -H 'Content-Type: application/json' \
  -d '{"name":"Atorvastatin","dosage":"20 mg","category":"Statin"}'

curl -i -X POST http://localhost:8080/api/diseases \
  -H 'Content-Type: application/json' \
  -d '{"name":"Hyperlipidaemia, unspecified","icd10Code":"E78.5"}'

curl -i -X POST http://localhost:8080/api/lab-tests \
  -H 'Content-Type: application/json' \
  -d '{"name":"Sodium","referenceRangeLow":135,"referenceRangeHigh":145,"unit":"mmol/L"}'

curl http://localhost:8080/api/lab-tests
```

### Clinical records — `/api/patients/{id}/vital-signs`, `/lab-results`, `/conditions`

Each supports `GET` (list), `POST` (add) and `DELETE /{recordId}`. Records
cannot be edited; delete and re-add instead. A missing patient gives 404.

| Record | Fields (* = required) | Checks |
|--------|-----------------------|--------|
| Vital signs | `recordedAt`* (e.g. `2026-10-13T09:30:00Z`), `systolicBp`, `diastolicBp` (mmHg), `temperature` (°C), `heartRate` (beats/min), `respiratoryRate` (breaths/min), `oxygenSaturation` (%), `notes` | At least one value. Limits: BP 40–300 / 20–200, temperature 25.0–45.0, heart rate 20–300, respiratory rate 4–80, SpO₂ 50–100. Both BP values together, systolic > diastolic. Not in the future. |
| Lab result | `labTestId`*, `value`*, `measuredAt`*, `unit`, `notes` | Lab test must exist (400). `unit`, if given, must equal the lab test's unit (no conversion). Response includes `flag`: `LOW`/`NORMAL`/`HIGH` against the reference range. |
| Condition | `diseaseId`*, `status` (`ACTIVE` default, `RESOLVED`), `onsetDate`, `notes` | Disease must exist (400); once per patient (409). Only ACTIVE conditions are used by rules. |

```bash
curl -i -X POST http://localhost:8080/api/patients/1/vital-signs \
  -H 'Content-Type: application/json' \
  -d '{"recordedAt":"2026-10-13T09:30:00Z","systolicBp":152,"diastolicBp":96,
       "temperature":38.6,"heartRate":118,"respiratoryRate":24,"oxygenSaturation":90}'

# lab test ids: curl http://localhost:8080/api/lab-tests
curl -i -X POST http://localhost:8080/api/patients/1/lab-results \
  -H 'Content-Type: application/json' \
  -d '{"labTestId":2,"value":8.2,"unit":"mmol/L","measuredAt":"2026-10-13T08:00:00Z"}'

curl -i -X POST http://localhost:8080/api/patients/1/conditions \
  -H 'Content-Type: application/json' -d '{"diseaseId":1,"status":"ACTIVE"}'
```

A lab test used by patient results, or a disease used in patient histories,
cannot be deleted (409).

### Clinical rules — `/api/rules`

| Method | Path | Result |
|--------|------|--------|
| GET | `/api/rules` | All rules, priority order (1 = most important) |
| GET | `/api/rules/{id}` | One rule |
| GET | `/api/rules/facts` | Facts and operators a condition may use |
| POST | `/api/rules/validate` | Check a rule without saving → 200 `{"valid": false, "errors": [...]}` |
| POST | `/api/rules` | Create → 201; invalid rule → 400 with every problem; duplicate `ruleId` → 409 |
| PUT | `/api/rules/{id}` | Replace → 200 (set `"enabled": false` to switch a rule off) |
| DELETE | `/api/rules/{id}` | Delete → 204 (past decisions keep their copy) |

The full rule JSON format (fields, facts, operators, AND/OR/NOT, missing
data, validation) is in [`../docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md) §5.
A small example:

```bash
curl -i -X POST http://localhost:8080/api/rules \
  -H 'Content-Type: application/json' \
  -d '{"ruleId":"HR-LOW","name":"Bradycardia","description":"Educational example.",
       "severity":"MEDIUM","priority":35,
       "condition":{"fact":"vitals.heartRate","operator":"lt","value":50},
       "recommendedActions":["Re-check the heart rate","Review medications that slow the heart rate"]}'
```

An invalid rule returns, for example:

```json
{ "status": 400, "title": "Bad Request", "detail": "Invalid rule",
  "errors": { "condition.fact": "unknown fact \"vitals.pulse\"" } }
```

### Decisions — `/api/patients/{id}/decisions`

| Method | Path | Result |
|--------|------|--------|
| POST | `/api/patients/{id}/decisions` | Evaluate every enabled rule now; the decision is stored → 201 + `Location` |
| GET | `/api/patients/{id}/decisions` | Earlier decisions, newest first |
| GET | `/api/patients/{id}/decisions/{decisionId}` | One decision |

```bash
curl -i -X POST http://localhost:8080/api/patients/1/decisions
```

Example response (shortened):

```json
{
  "id": 3, "patientId": 1, "requestedAt": "2026-10-13T10:00:00Z",
  "outcome": "RULES_MATCHED",
  "summary": "2 of 5 active rules matched. Review the reasons and suggested actions; a match is not a diagnosis. 1 rule could not be evaluated because data was missing or too old.",
  "rulesEvaluated": 5, "rulesNotMatched": 2,
  "matchedRules": [
    { "ruleId": "SPO2-LOW", "name": "Low oxygen saturation", "severity": "HIGH", "priority": 20,
      "reasons": ["Oxygen saturation is 90% (recorded 2026-10-13 09:30 UTC), which is less than 92%."],
      "recommendedActions": ["Re-check oxygen saturation and the sensor position", "..."] },
    { "ruleId": "HR-HIGH", "name": "Tachycardia", "severity": "MEDIUM", "priority": 30,
      "reasons": ["Heart rate is 118 beats/min (recorded 2026-10-13 09:30 UTC), which is greater than 100 beats/min."],
      "recommendedActions": ["Re-check the heart rate with the patient at rest", "..."] }
  ],
  "insufficientData": [
    { "ruleId": "GLUCOSE-HIGH", "name": "Elevated fasting blood glucose", "severity": "MEDIUM", "priority": 50,
      "reasons": ["No Fasting plasma glucose result in the last 72 hours"], "recommendedActions": [] }
  ],
  "invalidRules": [],
  "disclaimer": "Educational prototype using synthetic data. Matched rules are prompts for clinician review, not diagnoses. The rules are illustrative and not clinically validated."
}
```

- **`outcome`** is `RULES_MATCHED` or `NO_RULES_MATCHED`. With no match, the
  summary says this does not rule out any condition.
- **`insufficientData`** lists rules that could not be evaluated, because the
  needed values were never recorded or are older than the limits in
  `application.yml`: `cdss.decision.vital-signs-max-age` (24h) and
  `lab-results-max-age` (72h). Missing data is **never** treated as normal.
- **`invalidRules`** lists stored rules that no longer pass validation (for
  example, a lab test was renamed). They are skipped and the rest still run.

### Sample data

Migration `V3__sample_data.sql` adds 4 fictional patients, 5 drugs, 5
diseases and 5 lab tests so there is something to show. **Dosages and
reference ranges are educational examples only**. They are not prescribing
guidance, and must be checked against a local formulary and laboratory before
any real use. Their `notes` field says so.

Migration `V6__sample_rules.sql` adds five **illustrative** rules
(`SEPSIS-SCREEN`, `SPO2-LOW`, `HR-HIGH`, `BP-HIGH`, `GLUCOSE-HIGH`; see
[`../docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md) §5.7). It also adds
sample conditions for Alex Example (I10, E11.9) and Jordan Sample (J18.9,
resolved). No vital signs or lab results are seeded, so record some before
requesting a decision.

## 6. Tests

```bash
cd backend
./mvnw test                                                   # no database needed
CDSS_MYSQL_IT=true ./mvnw test                                # also run the MySQL integration tests
CDSS_MYSQL_IT=true ./mvnw test -Dtest=MySqlIntegrationTests   # only the MySQL tests
```

Expected result of `./mvnw test`:
`Tests run: 152, Failures: 0, Errors: 0, Skipped: 7`. The 7 skipped are
the MySQL integration tests, which only run when `CDSS_MYSQL_IT=true`. They
use your local `cdss` database through the `dev` profile, and each test is
rolled back, so they leave no data behind.

| Test class | What it checks | Needs MySQL |
|------------|----------------|:-----------:|
| `ruleengine.RuleParserTests` | Valid rules; every kind of invalid rule with its error path; depth and size limits | No |
| `ruleengine.RuleEvaluatorTests` | Every operator at and around the boundary; AND/OR/NOT; missing data → UNKNOWN; explanation text | No |
| `ruleengine.SampleRulesTests` | Reads the five rules from `V6__sample_rules.sql`; each fires/does not fire around its thresholds | No |
| `service.DecisionServiceTests` | Matched, no match, insufficient data, invalid stored rule, priority order, no rules, 404, history | No |
| `service.PatientFactsServiceTests` | Newest value per vital sign within 24 h, newest lab result per test, active conditions, age | No |
| `service.ClinicalRuleServiceTests` | Validation before save, duplicate `ruleId`, validate endpoint, facts list | No |
| `service.ClinicalRecordServiceTests` | Records per patient, unit checks, unknown lab test, duplicate condition | No |
| `controller.ClinicalRecordControllerTests` | 201/400/404/409; implausible vital signs; blood pressure rules; future timestamps | No |
| `controller.ClinicalRuleControllerTests` | 201; 400 with rule errors; missing fields; validate; facts; 404; 409 | No |
| `controller.DecisionControllerTests` | 201 with reasons/actions/disclaimer; no-match result; 404; bad ID; history | No |
| `ApplicationConfigurationTests` | `ddl-auto=validate`, env-only credentials, safe errors, decision age limits, open API only in `dev` | No |
| `config.SecurityConfigTests` | `/api` 401 by default; open when `cdss.security.open-api=true` | No |
| `controller.HealthControllerTests`, `PatientControllerTests`, `KnowledgeBaseControllerTests` | Health, patient and knowledge-base endpoints | No |
| `service.PatientServiceTests`, `KnowledgeBaseServiceTests`, `DatabaseHealthCheckerTests` | Patient/knowledge-base logic (incl. in-use deletes), health check | No |
| `MySqlIntegrationTests` | Real MySQL: migrations applied; all sample rules valid; abnormal values trigger all five rules; normal values match none; stale data ignored; cascade delete; in-use lab test; rule JSON stored | **Yes** (opt-in) |

## Layout

```
backend/
├── pom.xml
├── mvnw, mvnw.cmd, .mvn/wrapper/          # Maven Wrapper
└── src/
    ├── main/java/com/cdss/
    │   ├── CdssApplication.java
    │   ├── config/        # SecurityConfig, TimeConfig (UTC Clock), DecisionProperties
    │   ├── controller/    # REST endpoints (patients, knowledge base, records, rules, decisions, health)
    │   ├── service/       # business logic
    │   ├── repository/    # Spring Data JPA interfaces
    │   ├── entity/        # JPA entities and enums
    │   ├── dto/           # request/response records (with validation)
    │   ├── ruleengine/    # plain-Java rule parser and evaluator (no Spring)
    │   └── exception/     # GlobalExceptionHandler + custom exceptions
    ├── main/resources/
    │   ├── application.yml             # base config (env vars, decision age limits)
    │   ├── application-dev.yml         # dev profile: imports ../.env, open API
    │   └── db/migration/               # V1–V6 (see ../docs/ARCHITECTURE.md §4)
    └── test/java/com/cdss/             # mirrors the main packages
```

Each schema change is a new Flyway file `V<n>__<description>.sql`; never edit
one that has already run.
