# frontend/

React + TypeScript + Vite + Tailwind CSS user interface for the educational
CDSS prototype (synthetic data only — not for clinical use).

**Status:** step E complete. Every screen uses the real Spring Boot API.
There is **no mock data** and no second backend. **No login yet** (step F),
so anyone who can open the app can change data. Use it only on your own
machine.

## Run locally

Prerequisites: **Node.js 22 or 24 (LTS)** and the backend running on
`http://localhost:8080` (see [`../backend/README.md`](../backend/README.md)).

```bash
# terminal 1 — backend
cd backend
./mvnw spring-boot:run

# terminal 2 — frontend
cd frontend
npm install        # first time only (uses package-lock.json)
npm run dev        # http://localhost:5173
```

On macOS with Homebrew, install Node with `brew install node@24`.

| Command | What it does |
|---------|--------------|
| `npm run dev` | Development server on port 5173 with hot reload |
| `npm test` | Vitest + React Testing Library tests (no backend needed) |
| `npm run typecheck` | TypeScript check (`strict`) |
| `npm run build` | Type check, then production build into `dist/` |
| `npm run preview` | Serve the production build locally |

## How it talks to the backend

- All API calls go through **`src/api/client.ts`**, using one base URL:
  `VITE_API_BASE_URL`. The default is `/api`.
- In development, Vite forwards `/api/*` to `http://localhost:8080` (see
  `vite.config.ts`). The browser sees one origin, so **no CORS
  configuration** is needed and none was added to the backend.
- To use a different backend, copy `.env.example` to `.env.local` and set
  `VITE_API_BASE_URL`. A backend on another origin would also need CORS,
  which is not configured.
- **Never put secrets in `VITE_*` variables.** They are built into the
  JavaScript that browsers download. The frontend needs no credentials; the
  database password stays in the root `.env`, which only the backend reads.

## Screens

| Route | Screen | Backend endpoints used |
|-------|--------|------------------------|
| `/` | Dashboard: backend/database status, counts, active rules, how-to steps | `GET /api/health`, `GET /api/patients`, `/api/drugs`, `/api/diseases`, `/api/lab-tests`, `/api/rules` |
| `/patients` | Patient list, name search, delete | `GET /api/patients?search=`, `DELETE /api/patients/{id}` |
| `/patients/new`, `/patients/:id/edit` | Patient form with field-level validation errors | `POST /api/patients`, `GET`/`PUT /api/patients/{id}` |
| `/patients/:id` | Patient details + clinical panel (below) | `GET`/`DELETE /api/patients/{id}` |
| `/decisions` | Choose a patient, then the same clinical panel | `GET /api/patients` |
| `/drugs`, `/diseases`, `/lab-tests` | Knowledge base: list, add, edit, delete (409 "in use" messages shown) | `GET`/`POST /api/{drugs,diseases,lab-tests}`, `PUT`/`DELETE …/{id}` |
| `/rules` | Clinical rules: list, add/edit JSON rules, **Check rule**, enable/disable, delete, list of available facts | `GET`/`POST /api/rules`, `PUT`/`DELETE /api/rules/{id}`, `POST /api/rules/validate`, `GET /api/rules/facts` |

**Clinical panel** (tabs on the patient and decision pages):

| Tab | Backend endpoints |
|-----|-------------------|
| Request decision | `POST /api/patients/{id}/decisions` |
| Vital signs | `GET`/`POST /api/patients/{id}/vital-signs`, `DELETE …/vital-signs/{recordId}` |
| Lab results | `GET`/`POST /api/patients/{id}/lab-results`, `DELETE …/{recordId}`, `GET /api/lab-tests` |
| Conditions | `GET`/`POST /api/patients/{id}/conditions`, `DELETE …/{recordId}`, `GET /api/diseases` |
| Decision history | `GET /api/patients/{id}/decisions` |

Every backend endpoint is used except `GET /api/drugs/{id}`,
`/api/diseases/{id}`, `/api/lab-tests/{id}`, `/api/rules/{id}` and
`/api/patients/{id}/decisions/{decisionId}`. The list responses already
contain those records.

## Clinical safety in the UI

- A disclaimer bar on every page: *educational prototype, synthetic data only,
  not for clinical use; rule results are prompts for review, not
  diagnoses*.
- Every decision result shows the backend's disclaimer at the top. Matched
  rules show their **reasons** (the recorded values and times that met the
  condition) and their **suggested next steps**, as written in the rule.
- **No match** is labelled "No configured rule matched". The page shows the
  backend's summary ("does not rule out any condition") and adds that this
  "does not show that the patient is healthy".
- Rules that could not be checked are listed separately as
  "Could not be evaluated — data missing or too old". Invalid rules are listed
  too, never hidden.
- The frontend does not add medical advice of its own. All rule wording comes
  from the backend.

## Structure

```
frontend/
├── index.html, vite.config.ts, tsconfig.json, package.json, package-lock.json
├── .env.example            # VITE_API_BASE_URL (no secrets)
├── public/favicon.svg
└── src/
    ├── main.tsx, App.tsx   # entry point and routes
    ├── index.css           # Tailwind import
    ├── api/                # client.ts (fetch + errors), types.ts (backend DTOs), one file per resource
    ├── components/         # Layout, ClinicalPanel, DecisionResult, ui.tsx (Button, Card, Alert, Field, …), Icon
    ├── hooks/useAsync.ts   # loading / data / error for a request
    ├── pages/              # one file per screen
    ├── utils/format.ts     # dates, ages, optional form values
    └── test/               # test setup and fetch mock
```

- **Forms** keep their values in component state and send them to the
  backend. Empty optional fields are left out; empty required fields are sent
  as `null`. The backend validates everything.
- **Validation errors** from the backend appear under each field and in a
  summary list.
- **States:** every list shows a loading indicator, an empty state and an
  error message with **Try again**. If the backend is down, the message says
  how to start it.

## Tests

`npm test` runs 23 tests in 6 files. `fetch` is replaced by a small fake
(`src/test/fetchMock.ts`), so no backend is needed.

| File | Checks |
|------|--------|
| `api/client.test.ts` | JSON requests, 204, validation errors with field errors, 404/409 messages, backend unreachable |
| `components/DecisionResult.test.tsx` | Matched rules with reasons/actions and disclaimer; no-match wording never says the patient is healthy; insufficient-data list |
| `pages/PatientsPage.test.tsx` | Loading, list, empty state, search, error + retry |
| `pages/PatientFormPage.test.tsx` | Empty required fields sent as `null` and backend errors shown under fields; successful create |
| `pages/RulesPage.test.tsx` | Rules and facts listed; backend validation problems shown; invalid rule on save; non-JSON condition caught before sending |
| `utils/format.test.ts` | Age, optional values, date-time conversion |

## Limitations

- **No authentication** (step F). There's no login screen, and every action
  is available to anyone who can open the page. The top bar says "No login
  (development mode)".
- **Not available** because the backend has no endpoint for them:
  - decisions across all patients (history is per patient);
  - editing a clinical record (delete and re-add);
  - pagination;
  - medications and allergies as patient records.
- **No CORS** is configured. The app relies on the dev-server proxy (or on
  serving the built files from the same origin as the API).
