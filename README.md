# Clinical Decision Support System (CDSS) — Educational Prototype

> **Educational prototype — synthetic data only — not for clinical use.**
> This project has not been clinically validated, is not a medical device, and
> is not production-ready. It must never be used with real patient data or to
> guide real clinical decisions.

An undergraduate software engineering project: a small, complete Clinical
Decision Support System. Users manage synthetic patient records, maintain a
knowledge base of JSON clinical rules, and ask the system for decisions —
alerts and recommendations that explain which rule fired and why.

## Problem statement

Clinicians reviewing a patient have to combine vital signs, lab results,
conditions, medications and allergies, and it is easy to miss an abnormal
combination or a medication that conflicts with a recorded allergy. This
project shows how a decision support system can help: it evaluates
transparent, editable rules against a patient's recorded data, explains every
alert, checks a proposed medication against the patient's own allergies and
medications before it is saved, and keeps the clinician in control. It is a
teaching demonstration of that design, not a clinical tool.

## Development status

Implementation follows steps A–H in
[`docs/PROJECT_PLAN.md`](docs/PROJECT_PLAN.md). **Steps A–H are implemented
and pass their automated tests.** Flyway migrations V1–V12 are applied to the
local MySQL database. The repository owner has confirmed the main clinician
and administrator workflows in the browser (2026-10-14); the full manual
checklist in [`docs/DEMO.md`](docs/DEMO.md) §4 has not been formally ticked.

## Technology stack and architecture

| Layer | Technology |
|-------|------------|
| Frontend | React, TypeScript, Vite, Tailwind CSS, React Router (`frontend/`) |
| Backend | Java 21, Spring Boot 4.1, Spring Web MVC, Spring Data JPA, Spring Security, Bean Validation (`backend/`) |
| Database | MySQL 8, installed locally; schema managed by Flyway; Hibernate `ddl-auto=validate` |
| Rule engine | Plain Java: JSON rules parsed with Jackson, hand-written validator, recursive evaluator |
| API | REST + JSON under `/api` |
| Auth | Username/password login, server-side session cookie (HttpOnly, SameSite=Strict), BCrypt, CSRF tokens, roles `ADMIN` / `CLINICIAN` |
| Tests | JUnit 5, Mockito, `@WebMvcTest`; Vitest + React Testing Library |

```
Browser (React, :5173) ──/api proxy──▶ Spring Boot (127.0.0.1:8080) ──JPA──▶ MySQL (local)
                                       Controller → Service → Repository → Entity
                                       DTOs at the API boundary; rule engine and
                                       medication safety checker are plain Java
```

There is exactly one backend. No Docker, cloud services, message queues or
token systems are used. Full detail: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Features

> **Implemented** = built and covered by automated tests.

**For clinicians (`CLINICIAN`)**

- Clinical overview dashboard: patient and follow-up counts, follow-ups due, recent patients.
- Synthetic patients: create, view, edit, search by name.
- Clinical records: vital signs, lab results, conditions (with history).
- Request a clinical decision: matched rules with reasons, suggested actions,
  "insufficient data" for rules without recent data, and the backend disclaimer;
  decision history.
- Medications: add (only after a safety check), edit, discontinue (with a
  reason), complete; active list vs history. Nothing is deleted.
- Allergies: record, edit, resolve; "no known allergies" vs "not assessed".
- Follow-ups: schedule, reschedule, complete, cancel; overdue / upcoming lists.
- Read-only reference: drugs, diseases, lab tests, and decision rules in plain
  English (no rule JSON). Own profile page.

**For administrators (`ADMIN`)** — everything above, plus:

- Administration dashboard with live system health (`GET /api/health`), user,
  rule and catalogue figures.
- User management: create accounts, activate/deactivate, change role (with
  self and last-admin guards; affected sessions are ended).
- Knowledge base editing: drugs, diseases (ICD-10), lab tests with reference ranges.
- Clinical rule management: builder, plain-English preview, validation, dry
  run on a patient, activate/deactivate, priority, search.
- Delete patients.

Roles are enforced by the backend (`SecurityConfig`, tested for every
endpoint), not only by hiding buttons.

## Medication interaction and allergy feedback workflow

1. A clinician chooses a drug on the patient's **Medications** tab and clicks
   **Run safety check** (`POST /api/patients/{id}/medications/safety-check`;
   nothing is saved).
2. The backend compares the drug with the patient's **active allergies**
   (exact drug name or exact knowledge-base category), **active medications**
   (duplicate therapy), and the **drug-interaction dataset**.
3. The panel lists each finding with: result (match found / uncertain / no
   match), review priority, severity (from the record or dataset), medications
   and recorded allergens involved, explanation, mechanism and recommended
   action (*"Not provided by the source"* when the source has none), and the
   source with its classification — **Patient record**, **Illustrative sample
   data**, or **Trusted source** — plus version, review date and review status
   when recorded.
4. Missing data is never treated as normal: an unassessed allergy history, a
   free-text allergy, or a missing interaction dataset gives **uncertain**;
   finding nothing says "no issues found in the available data", never "safe".
5. Each warning that needs review must be ticked and a reason given before
   **Save** is enabled. The backend re-runs the check on save and refuses
   (409) if any warning was not acknowledged.
6. If medication records change, or the data changed between check and save,
   the check is re-run. Acknowledgements carry over only for unchanged
   findings; new or materially changed findings are marked and must be
   reviewed again.
7. The system never prescribes, stops or changes a medication. Custom
   clinical rules are evaluated separately and do not affect this check.

> **Interaction data is illustrative.** No trustworthy clinical knowledge
> source is configured. The dataset holds **two illustrative sample rows**
> (Warfarin + Ibuprofen, Lisinopril + Ibuprofen), not clinically reviewed and
> without mechanism, recommended action, version or review date. Every
> interaction result is labelled as sample data, and any other drug pair is
> effectively unchecked.

## Database and migrations

Local MySQL 8; Flyway runs the SQL files in
`backend/src/main/resources/db/migration` on startup. Applied migrations are
never edited; each change is a new file.

| Migration | Contents |
|-----------|----------|
| V1 | Baseline |
| V2 | `patients`, `drugs`, `diseases`, `lab_tests` |
| V3 | Synthetic sample patients and knowledge-base examples |
| V4 | `vital_signs`, `lab_results`, `patient_conditions` |
| V5 | `clinical_rules`, `clinical_decisions`, `decision_rule_results` |
| V6 | 5 illustrative sample rules |
| V7 | `users` (demo users are created at startup from `.env`) |
| V8 | `patient_medications`, `patient_allergies` |
| V9 | `drug_interactions` + 2 illustrative sample rows |
| V10 | `follow_ups` |
| V11 | Data only: fictional names for the 4 untouched seed patients |
| V12 | Optional, nullable interaction source-detail columns (empty for the sample rows) |

Table-level detail: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) §4.

## Setup and run

You need **JDK 21**, **MySQL 8** and **Node.js 22/24 LTS** installed locally.
One-time database setup and troubleshooting: [`backend/README.md`](backend/README.md).

```bash
cp .env.example .env            # then set CDSS_DB_PASSWORD and the two demo
                                # passwords (one-time, see docs/DEMO.md)

# terminal 1 — backend on http://127.0.0.1:8080
cd backend
./mvnw spring-boot:run

# terminal 2 — frontend on http://localhost:5173
cd frontend
npm install                     # first time only
npm run dev
```

### Demo login

Open http://localhost:5173 and log in as **`admin`** or **`clinician`**. The
passwords are the values **you** set locally in `.env`
(`CDSS_DEMO_ADMIN_PASSWORD`, `CDSS_DEMO_CLINICIAN_PASSWORD`); they are never
stored in the repository. The backend creates each demo user on first start.
Details: [`docs/DEMO.md`](docs/DEMO.md) §1.

## Testing

```bash
cd backend && ./mvnw test                  # unit + web tests, no database needed
cd frontend && npm test && npm run build   # frontend tests, type check, production build
```

Results on 2026-10-14:

| Suite | Result |
|-------|--------|
| Backend `./mvnw test` | 499 tests run, 0 failures, 0 errors, 9 skipped |
| Skipped | The 9 opt-in `MySqlIntegrationTests` (`CDSS_MYSQL_IT=true`); not run, because they use the development database |
| Frontend `npm test` | 75 tests in 17 files pass |
| Frontend `npm run build` | Type check and production build succeed |
| Manual | Main clinician and admin workflows confirmed in the browser by the owner; full checklist in `docs/DEMO.md` §4 not formally ticked |

## Known limitations

- Not clinically validated, not a medical device, not production-ready;
  synthetic data only.
- Sample rules and their thresholds are illustrative, not clinical guidance.
  A matched rule is a prompt for review, not a diagnosis; "no rules matched"
  does not mean the patient is well.
- **Drug-interaction data is a two-row illustrative sample**, not a verified
  or licensed source; there is no screen to edit it.
- Allergy matching uses exact drug names and categories only (no ingredient
  or cross-reactivity data). The safety check does not consider doses,
  kidney/liver function, pregnancy or conditions, and existing medications
  are not re-checked in the background.
- Only recorded, recent observations are used; plausible data-entry mistakes
  cannot be detected.
- Local HTTP only (cookie not `Secure`), no login rate limiting, no password
  reset, no audit trail; sessions are in memory.

More: [`docs/DEMO.md`](docs/DEMO.md) §5.

## Repository layout

```
.
├── CLAUDE.md          # Project rules: scope, conventions, security, testing, workflow
├── README.md
├── .env.example       # Settings template — copy to .env (git-ignored)
├── docs/
│   ├── PROJECT_PLAN.md
│   ├── ARCHITECTURE.md
│   └── DEMO.md        # Demo accounts, demo script, manual test checklist
├── backend/           # Spring Boot app
├── frontend/          # React app
└── tests/             # README only — tests live in backend/ and frontend/
```

## Documentation

- [`CLAUDE.md`](CLAUDE.md) — project rules, conventions, security and testing requirements
- [`docs/PROJECT_PLAN.md`](docs/PROJECT_PLAN.md) — steps A–H and what "done" means for each
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — architecture, data model, API, rule engine, security, decision log
- [`docs/DEMO.md`](docs/DEMO.md) — demo accounts, demo script, manual test checklist
- [`backend/README.md`](backend/README.md) — backend setup, run, and test instructions
- [`frontend/README.md`](frontend/README.md) — frontend setup, screens, connected endpoints
