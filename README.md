# Clinical Decision Support System (CDSS) — Educational Prototype

> **Educational prototype — synthetic data only — not for clinical use.**
> This project has not been clinically validated, is not a medical device, and
> is not production-ready. It must never be used with real patient data or to
> guide real clinical decisions.

An undergraduate software engineering project: a small, complete Clinical
Decision Support System. Users manage synthetic patient records, maintain a
knowledge base of JSON clinical rules, and ask the system for decisions —
alerts and recommendations that explain which rule fired and why.

## Development status

Implementation follows steps A–F in
[`docs/PROJECT_PLAN.md`](docs/PROJECT_PLAN.md).
**Steps A–E are done; step F (login, final tests and demo) is next.**
The Spring Boot backend runs against a local MySQL 8.4 database and provides
REST APIs for patients, their clinical records, the knowledge base, JSON
clinical rules, and clinical decisions with stored history. 145 automated
tests pass without a database; 7 more integration tests pass against MySQL
when enabled. A React frontend covers every backend feature (23 frontend
tests pass). There is no login yet: in the local `dev` profile the APIs and
the UI are open to anyone who can reach them (step F adds login).
**No clinical features, login, or user interface are implemented yet.**

## Features

> **Implemented** = built and tested. **Planned** = designed, not built.

| Feature | Step | Status |
|---------|------|--------|
| Backend health endpoint (`GET /api/health`, reports database separately) | A | Implemented |
| Backend connected to a locally installed MySQL database | B | Implemented |
| Synthetic patients: create, view, edit, delete, search by name | C | Implemented (API) |
| Knowledge base: drugs, diseases (ICD-10), lab tests with reference ranges | C | Implemented (API) |
| Patient clinical records: vital signs, lab results, conditions (history) | D | Implemented (API) |
| Clinical rules stored as JSON; Java rule engine with rule validation | D | Implemented (API) |
| Decisions: run rules for a patient, see matched rules with reasons and suggested actions, view history | D | Implemented (API) |
| React web interface for all of the above (built in step E — no existing frontend was supplied) | E | Implemented |
| Login with roles (`ADMIN`, `CLINICIAN`) | F | Planned |
| Automated backend tests and a demo checklist | A–F | Partial (145 tests + 7 MySQL integration tests; checklist in F) |

## Technology stack

| Layer | Technology |
|-------|------------|
| Frontend | React, TypeScript, Vite, Tailwind CSS, React Router |
| Backend | Java 21, Spring Boot 4.1, Spring Web, Spring Data JPA, Spring Security, Bean Validation |
| Database | MySQL 8, installed locally; tables created by Flyway |
| Rule engine | Plain Java evaluating JSON rules |
| API | REST + JSON under `/api` |
| Tests | JUnit 5, Mockito, Spring Boot test slices; Vitest + React Testing Library |

No Docker, cloud services, or token systems are used.

## Repository layout

```
.
├── CLAUDE.md          # Project rules: scope, conventions, security, testing, workflow
├── README.md
├── .env.example       # Settings template — copy to .env (git-ignored)
├── docs/
│   ├── PROJECT_PLAN.md
│   └── ARCHITECTURE.md
├── backend/           # Spring Boot app
├── frontend/          # React app
└── tests/             # README only — tests live in backend/ and frontend/
```

## Getting started

You need **JDK 21**, **MySQL 8** and **Node.js 22/24 LTS** installed locally.
One-time database setup and troubleshooting: [`backend/README.md`](backend/README.md).

```bash
cp .env.example .env            # then set CDSS_DB_PASSWORD (one-time)

# terminal 1 — backend on http://localhost:8080
cd backend
./mvnw spring-boot:run

# terminal 2 — frontend on http://localhost:5173
cd frontend
npm install                     # first time only
npm run dev
```

Open http://localhost:5173. Tests: `cd backend && ./mvnw test` and
`cd frontend && npm test`.

## Documentation

- [`CLAUDE.md`](CLAUDE.md) — project rules, conventions, security and testing requirements
- [`docs/PROJECT_PLAN.md`](docs/PROJECT_PLAN.md) — steps A–F and what "done" means for each
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — architecture, data model, API, rule engine, decision log
- [`backend/README.md`](backend/README.md) — backend setup, run, and test instructions
- [`frontend/README.md`](frontend/README.md) — frontend setup, screens, connected endpoints

## Disclaimer and clinical safety limitations

All patient data in this project is synthetic. Example clinical rules are
simplified illustrations for teaching purposes and are not clinical guidance.

- Rule thresholds are **not clinically validated**.
- A matched rule is a prompt for clinician review, **not a diagnosis**.
- "No rules matched" does **not** mean the patient is well. Rules that lack
  recent data are reported separately as "insufficient data".
- Only recorded, recent observations are used. Implausible vital signs are
  rejected, but plausible mistakes cannot be detected.

Details: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) §5.
