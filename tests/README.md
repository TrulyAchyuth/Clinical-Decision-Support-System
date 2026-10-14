# tests/

No test code lives here. Automated tests sit next to the code they test:

- Backend: `backend/src/test/java/...` — run with `cd backend && ./mvnw test`
  (JUnit 5, Mockito, Spring Boot test slices; no database needed).
- Frontend: `frontend/src/**/*.test.ts(x)` — run with `cd frontend && npm test`
  (Vitest + React Testing Library; `fetch` is mocked, no backend needed).

The whole system (MySQL, backend, frontend together) is checked by following
the manual demo checklist in `docs/DEMO.md`, written in step F.

See the testing strategy in
[`../docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md) §9 and the testing rules
in [`../CLAUDE.md`](../CLAUDE.md) §7.
