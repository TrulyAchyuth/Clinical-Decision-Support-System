# frontend/

React + TypeScript + Vite + Tailwind CSS user interface for the educational
CDSS prototype (synthetic data only — not for clinical use).

**Status:** steps E–H implemented (all screens, login and roles, medications,
allergies, safety feedback, follow-ups, admin workspace). Every screen uses the real Spring
Boot API. There is **no mock data** and no second backend. Users log in as
`admin` or `clinician` (demo accounts: [`../docs/DEMO.md`](../docs/DEMO.md));
the backend enforces the roles. Use it only on your own machine.

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
- In development, Vite (listening on `localhost:5173` only) forwards `/api/*`
  to `http://127.0.0.1:8080`, where the backend's `dev` profile listens (see
  `vite.config.ts`). The browser sees one origin, so **no CORS
  configuration** is needed and none was added to the backend.
- To use a different backend, copy `.env.example` to `.env.local` and set
  `VITE_API_BASE_URL`. A backend on another origin would also need CORS,
  which is not configured.
- **Login:** `src/components/AuthProvider.tsx` asks `GET /api/auth/me` who
  is logged in and keeps the user in React context. `RequireAuth` sends
  anyone not logged in to `/login`, then back to the page they wanted. If
  any request answers 401, the user is returned to the login page with
  "Your session has ended".
- **CSRF:** `client.ts` fetches the token from `GET /api/auth/csrf`, keeps it
  in memory (never in a cookie or `localStorage`) and sends it as
  `X-CSRF-TOKEN` on every POST, PUT and DELETE. If the backend rejects the
  token, the client fetches a new one and retries **once**. Login and logout
  clear the token because the server replaces it.
- **Roles:** buttons for admin-only actions (add/edit/delete drugs, diseases,
  lab tests and rules; delete patients) are hidden from clinicians, who see a
  "Read-only" note. This is only for convenience: the backend refuses these
  requests (403) whatever the UI shows.
- **Never put secrets in `VITE_*` variables.** They are built into the
  JavaScript that browsers download. The frontend needs no credentials; the
  database password stays in the root `.env`, which only the backend reads.

## Screens

| Route | Screen | Backend endpoints used |
|-------|--------|------------------------|
| `/profile` | My profile: username, role, account status, what the role allows | `GET /api/auth/me` (via the auth context) |
| `/admin/users` | **Admin only:** user list with search and filters; create account; activate/deactivate; change role | `GET`/`POST /api/admin/users`, `POST …/{id}/activate`, `…/deactivate`, `PUT …/{id}/role` |
| `/reference/rules` | **Clinicians:** decision rules in plain English (no JSON) | `GET /api/rules/summaries` |
| `/login` | Login form; "session ended" and backend-unreachable messages | `GET /api/auth/csrf`, `POST /api/auth/login`, `GET /api/auth/me` |
| *(top bar)* | Username, role, **Log out** | `POST /api/auth/logout` |
| `/` | Clinician: clinical overview (patient and follow-up counts, quick actions, due follow-ups, recently updated patients). Admin: system health, user, rule and catalogue figures, admin actions | Clinician: `GET /api/patients`, `GET /api/follow-ups`. Admin: `GET /api/health`, `/api/admin/users`, `/api/rules`, `/api/drugs`, `/api/diseases`, `/api/lab-tests` |
| `/patients` | Patient list, name search, delete (admin) | `GET /api/patients?search=`, `DELETE /api/patients/{id}` |
| `/patients/new`, `/patients/:id/edit` | Patient form with field-level validation errors | `POST /api/patients`, `GET`/`PUT /api/patients/{id}` |
| `/patients/:id` | Patient details + clinical panel (below) | `GET`/`DELETE /api/patients/{id}` |
| `/decisions` | Choose a patient, then the same clinical panel | `GET /api/patients` |
| `/drugs`, `/diseases`, `/lab-tests` | Knowledge base: list; add, edit, delete for admins (409 "in use" messages shown) | `GET`/`POST /api/{drugs,diseases,lab-tests}`, `PUT`/`DELETE …/{id}` |
| `/follow-ups` | All follow-ups grouped as overdue / upcoming / later / completed / cancelled; reschedule, complete, cancel | `GET /api/follow-ups`, `POST …/follow-ups/{id}/reschedule`, `/complete`, `/cancel` |
| `/rules` | Clinical rules: search, status filter, details in plain English; for admins a condition builder (plus JSON), **Check rule**, dry run on a patient, activate/deactivate (with confirmation), delete | `GET`/`POST /api/rules`, `PUT`/`DELETE /api/rules/{id}`, `POST /api/rules/validate`, `/dry-run`, `/{id}/activate`, `/{id}/deactivate`, `GET /api/rules/facts` |

**Clinical panel** (tabs on the patient and decision pages):

| Tab | Backend endpoints |
|-----|-------------------|
| Request decision | `POST /api/patients/{id}/decisions` |
| Vital signs | `GET`/`POST /api/patients/{id}/vital-signs`, `DELETE …/vital-signs/{recordId}` |
| Lab results | `GET`/`POST /api/patients/{id}/lab-results`, `DELETE …/{recordId}`, `GET /api/lab-tests` |
| Conditions | `GET`/`POST /api/patients/{id}/conditions`, `DELETE …/{recordId}`, `GET /api/diseases` |
| Medications | `GET`/`POST /api/patients/{id}/medications`, `POST …/safety-check`, `PUT …/{medicationId}`, `POST …/discontinue`, `…/complete` |
| Allergies (also the banner at the top of the patient page) | `GET`/`POST /api/patients/{id}/allergies`, `PUT …/{allergyId}`, `POST …/resolve`, `…/no-known-allergies` |
| Follow-ups | `GET`/`POST /api/patients/{id}/follow-ups` and the actions above |
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
    ├── components/         # AuthProvider (login state, RequireAuth), Layout, ClinicalPanel, DecisionResult, ui.tsx, Icon
    ├── hooks/useAsync.ts   # loading / data / error for a request
    ├── pages/              # one file per screen
    ├── utils/format.ts     # dates, ages, optional form values
    └── test/               # test setup, fetch mock, renderAs (page as admin/clinician)
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

`npm test` runs 75 tests in 17 files. `fetch` is replaced by a small fake
(`src/test/fetchMock.ts`, which also answers the CSRF-token request), so no
backend is needed. `src/test/renderAs.tsx` renders a page as an admin or a
clinician without calling the login endpoints.

| File | Checks |
|------|--------|
| `api/client.test.ts` | JSON requests, 204, validation errors with field errors, 404/409 messages, backend unreachable; CSRF token sent on changes only, refreshed and retried once, no endless retry; role 403 not retried; 401 reported as "session ended" except for auth endpoints |
| `components/AuthProvider.test.tsx` | Logged-out user sent to login and back after login (with CSRF token); valid session goes straight in; wrong password message and cleared password; session ended → login page with message; logout; backend unreachable |
| `pages/DrugsPage.test.tsx` | Admin sees add/edit/delete; clinician sees a read-only list |
| `components/MedicationsTab.test.tsx` | Active vs history; save blocked until the safety check is run, warnings ticked and a reason given (and both are sent); sample-data banner; "nothing found" never shown as safe; finding details with "Not provided by the source"; re-check after a medication change keeps only unchanged acknowledgements and marks new findings; discontinue needs a reason |
| `components/AllergiesTab.test.tsx` | "Not assessed" never shown as "no known allergies"; NKA after confirmation; drug allergy linked to the knowledge base; backend validation message |
| `pages/FollowUpsPage.test.tsx` | Grouping by timing (completed never overdue), cancel needs a reason, empty states |
| `components/ProfileMenu.test.tsx` | Username/role/initials; keyboard open, arrow keys, Escape returns focus; outside click; My profile; logout |
| `components/RoleWorkspace.test.tsx` | Clinician vs admin navigation; health indicator only for admins; access denied on admin routes |
| `pages/Dashboards.test.tsx` | Clinician metrics and lists from real data (no admin/health calls); admin health healthy/degraded/unavailable, user and rule figures |
| `pages/UsersPage.test.tsx` | No password data, no actions on your own row, filters, create with role, backend refusal shown |
| `pages/RuleSummariesPage.test.tsx` | Plain-English rules from `/api/rules/summaries` only, no JSON |
| `components/RuleConditionTools.test.ts` | Plain-English description, readable error paths, builder round trip / nested conditions refused |
| `components/DecisionResult.test.tsx` | Matched rules with reasons/actions and disclaimer; no-match wording never says the patient is healthy; insufficient-data list |
| `pages/PatientsPage.test.tsx` | Loading, list, empty state, search, error + retry; Delete only for admins |
| `pages/PatientFormPage.test.tsx` | Empty required fields sent as `null` and backend errors shown under fields; successful create |
| `pages/RulesPage.test.tsx` | Rules and facts listed; backend validation problems shown; invalid rule on save; non-JSON condition caught before sending; read-only for clinicians; activation with confirmation and its refusal; builder → JSON → plain English → dry run; search and status filter |
| `utils/format.test.ts` | Age, optional values, date-time conversion |

## Limitations

- **Accounts** come only from the backend's demo-user setup (`.env`); there
  are no screens to manage users or change passwords.
- **Not available** because the backend has no endpoint for them:
  - decisions across all patients (history is per patient);
  - editing a clinical record (delete and re-add);
  - pagination;
  - editing the drug-interaction sample data (read-only, `GET /api/drug-interactions`).
- **No CORS** is configured. The app relies on the dev-server proxy (or on
  serving the built files from the same origin as the API).
