# Demo guide and manual test checklist

> **Educational prototype — synthetic data only — not for clinical use.**
> The sample rules are illustrative and not clinically validated. A matched
> rule is a prompt for clinician review, not a diagnosis.

This guide sets up the two demo accounts, walks through a short demo, and
lists the manual checks for the whole system (backend, database and browser
together). The automated tests are described in
[`../backend/README.md`](../backend/README.md) §6 and
[`../frontend/README.md`](../frontend/README.md).

**All patient records are synthetic.** The four seed patients (Daniel
Okafor, Priya Raman, Sam Lindqvist, Emma Castillo) are fictional people
created for demonstration (migrations V3 and V11); their phone numbers use the
fictional 555-01xx range and their e-mail addresses the reserved
`example.com` domain. Do not enter real patient data.

## 1. Demo accounts (one-time setup)

There are two fixed demo usernames:

| Username | Role | May do |
|----------|------|--------|
| `admin` | `ADMIN` | Everything, including deleting patients, managing users, and changing drugs, diseases, lab tests and clinical rules |
| `clinician` | `CLINICIAN` | Manage patients (except delete), vital signs, lab results, conditions, medications, allergies and follow-ups; run decisions and view history; read-only drugs, diseases, lab tests and plain-English rules; no user management or rule JSON |

Their **passwords are never in the repository.** You choose them locally:

1. Make sure the repository-root `.env` exists (`cp .env.example .env`, see
   [`../backend/README.md`](../backend/README.md) §3). `.env` is git-ignored.
2. Add two passwords of 8–72 characters, for example generated with
   `openssl rand -base64 18`:

   ```
   CDSS_DEMO_ADMIN_PASSWORD=<your admin password>
   CDSS_DEMO_CLINICIAN_PASSWORD=<your clinician password>
   ```

3. Start the backend (`cd backend && ./mvnw spring-boot:run`). On startup
   it creates each demo user **that does not exist yet**, storing only a
   BCrypt hash. The log shows one line per user, for example
   `Demo user 'admin' created with role ADMIN`, or why it was skipped
   (password not set, or not 8–72 characters). Passwords are never logged.

**Changing a demo password later:** editing `.env` does not change an
existing user (so a typo cannot silently lock you out or reset an account).
To set a new password, remove that login yourself and restart the backend,
which creates it again from `.env`. For example, as the MySQL root user:
`DELETE FROM cdss.users WHERE username = 'clinician';`. This removes only the
login; no patient data is linked to users.

## 2. Start the system

```bash
cd backend && ./mvnw spring-boot:run      # terminal 1 (needs local MySQL)
cd frontend && npm run dev                # terminal 2 → http://localhost:5173
```

## 3. Short demo (3–5 minutes)

Logged in as `clinician`, on seed patient *Daniel Okafor*:

1. **Login page** shows the disclaimer; log in → clinical overview (no admin
   navigation or system health).
2. **Vital signs** → record heart rate 118, temperature 38.6. **Request
   decision** → matched rules with reasons, "insufficient data" for others,
   and the disclaimer.
3. **Allergies** → record a Drug allergy to *Amoxicillin* (Severe, "Hives").
4. **Medications** → choose *Amoxicillin 500 mg*, **Run safety check**: allergy
   match with recorded allergen, priority, source "Patient record"; Save is
   disabled until ticked and a reason is given. Choose another drug instead.
5. Add *Warfarin 5 mg*; then check *Ibuprofen 400 mg*: interaction match
   labelled **Illustrative sample data**, mechanism / recommended action
   "Not provided by the source". Discontinue Warfarin → the check re-runs and
   the interaction disappears.
6. Log out; log in as `admin` → administration overview with live health →
   **User management** → **Clinical rules** (builder, dry run). As a
   clinician, `/admin/users` shows "You don't have access" and the API
   returns 403.

## 3a. Full demo script (about 10 minutes)

1. Open <http://localhost:5173>. You are sent to the login page.
2. Log in as `clinician` with a **wrong** password → "Invalid username or
   password". Then log in with the right one → the **clinical overview**
   (patient and follow-up counts from the database, quick actions, follow-ups
   due, recently updated patients). The account menu (top right) shows
   `clinician` / Clinician. There is no system-health or admin navigation.
3. **Patients** → add a new synthetic patient (any made-up name).
4. Open the patient → **Vital signs**: record heart rate 118, temperature
   38.6, respiratory rate 24, oxygen saturation 90.
5. **Request decision** tab → *Request clinical decision*. Show the matched rules, their
   reasons, the suggested actions and the disclaimer. Point out that the
   rules are prompts for review, not diagnoses, and that rules without recent
   data are listed as "insufficient data", never as normal.
6. **Decision history** tab → the decision is stored.
7. **Drugs** → read-only for a clinician (no Add/Edit/Delete). **Decision
   rules** shows each rule in plain English, without its JSON. Typing
   `/rules` or `/admin/users` in the address bar shows "You don't have access
   to this page". The patient page has no **Delete** button.
8. Account menu → **My profile** shows username, role and what the role
   allows. Account menu → **Log out**. Using the browser's back button does
   not show patient data; you are back on the login page.
9. Log in as `admin` → the **administration overview**: live system health,
   user counts by role and status, rule counts (including rules failing
   validation) and catalogue totals.
10. **User management** → create a clinician account, deactivate it
   (confirmation), reactivate it. Your own row has no actions; deactivating
   or demoting the last active administrator is refused.
11. Add a drug, deactivate and re-activate a rule, and delete the patient
   created in step 3.

### 3b. Step G features (about 10 more minutes)

Use one of the synthetic seed patients (e.g. Daniel Okafor), logged in as `clinician`.

1. **Allergies tab** (the banner at the top of the patient page shows the
   same status): it says **Allergy history not assessed**, not "no
   allergies". Record a **Drug** allergy to *Amoxicillin* (reaction "Hives",
   severity Severe). The banner turns red: *Allergies recorded*.
2. **Medications tab** → choose *Amoxicillin 500 mg*, fill in dose, unit and
   frequency, click **1. Run safety check**: an allergy **match found**,
   review required. **2. Save medication** stays disabled until the warning
   is ticked as reviewed *and* a reason is entered. Cancel instead (choose
   another drug).
3. Add *Warfarin 5 mg* (run the check, acknowledge anything flagged, save).
   Then choose *Ibuprofen 400 mg* and run the check: a **drug interaction**
   match with Warfarin, marked **ILLUSTRATIVE SAMPLE DATA**. Point out that a
   check finding nothing says "no issues found in the available data", never
   "safe".
4. Acknowledge, give a reason and save Ibuprofen. In the **Active
   medications** table the row shows "1 warning(s) acknowledged". Click
   **Discontinue** on it, give a reason: it moves to **Medication history**
   with its dates and reason kept.
5. **Follow-ups tab** → schedule a follow-up for tomorrow ("BP review"). Open
   **Follow-ups** in the sidebar: it is under *Upcoming*; the dashboard shows
   it in *Follow-ups due*. Reschedule it, then complete it: it moves to
   *Completed* and is never shown as overdue.
6. Log in as `admin` → **Clinical rules**. Use the search box and the status
   filter. **Add rule**: pick *Heart rate* / *is greater than* / 120 in the
   builder, read the plain-English line, pick a patient under **Dry run** and
   click **Test on patient** (nothing is saved). Save the rule disabled, then
   **Activate** it (confirmation; the backend validates it first) and
   **Deactivate** it again.

## 4. Manual test checklist

Tick each item when it behaves as described. Record the date and who ran
it. *Status: the owner confirmed the main clinician and administrator
workflows in the browser on 2026-10-14; the items below have not been formally
ticked one by one.*

**Login and session**

- [ ] Opening any page while logged out shows the login page.
- [ ] Wrong password, and an unknown username, both show "Invalid username or password".
- [ ] Correct login returns to the page you originally asked for.
- [ ] The top bar shows the username and role.
- [ ] Reloading the page keeps you logged in.
- [ ] **Log out** returns to the login page; back button does not show data.
- [ ] In browser dev tools (Application → Cookies), `JSESSIONID` is `HttpOnly` and `SameSite=Strict`, and there is no CSRF cookie.
- [ ] After the session expires (30 minutes idle, or delete the `JSESSIONID` cookie), the next action returns to the login page with "Your session has ended".

**Roles (clinician)**

- [ ] Can create and edit patients, record vital signs, lab results and conditions, delete those records, run decisions and view history.
- [ ] Sees drugs, diseases, lab tests and rules read-only, with a "Read-only" note.
- [ ] Has no **Delete** button on the patient page.

**Roles (admin)**

- [ ] Can add, edit and delete drugs, diseases, lab tests and rules; can check (validate) a rule; can enable/disable rules.
- [ ] Can delete a patient.

**Backend enforcement (not just hidden buttons)** — use the curl helper in
[`../backend/README.md`](../backend/README.md) §5:

- [ ] Logged out: `api http://localhost:8080/api/patients` → 401.
- [ ] As `clinician`: `api -i -X DELETE http://localhost:8080/api/drugs/1` → 403 "Your role does not allow this action" (the drug is not deleted).
- [ ] As `clinician`, a change without the CSRF header, e.g.
      `curl -i -b "$COOKIES" -X POST http://localhost:8080/api/patients/1/decisions` → 403 with `"code": "CSRF"`.
- [ ] `curl -i http://localhost:8080/api/health` works without login and shows only status, database UP/DOWN and a timestamp.
- [ ] `lsof -nP -iTCP:8080 -sTCP:LISTEN` shows `127.0.0.1:8080` (not `*:8080`); from another device on the network, `http://<this computer's IP>:8080/api/health` does not connect.

**Step G: medications, allergies, safety check**

- [ ] New patient: allergy banner says "Allergy history not assessed"; the safety check reports *uncertain*, not safe.
- [ ] "Confirm no known allergies" works only while no allergy is active; recording an allergy afterwards clears it.
- [ ] Drug allergy matches every strength of that drug; a drug-category allergy matches drugs in exactly that category; an OTHER (free-text) allergy gives an *uncertain* warning.
- [ ] Save is impossible until every warning that needs review is ticked and a reason is given; the API refuses it too (409 `SAFETY_ACKNOWLEDGEMENT_REQUIRED` without `acknowledgedWarnings`).
- [ ] Every interaction warning is labelled illustrative sample data; "no match" never says safe.
- [ ] Each finding shows priority, medications, recorded allergens, source label; mechanism and recommended action say "Not provided by the source" for the sample rows.
- [ ] With a check open, discontinuing a medication re-runs it; unchanged warnings stay ticked, new or changed ones are marked and unticked.
- [ ] Adding the same drug twice while active → 409; discontinue needs a reason; discontinued/completed medications cannot be edited; nothing can be deleted.
- [ ] A drug used in a medication or allergy cannot be deleted from the knowledge base (409).
- [ ] Records persist after a page refresh and after a backend restart.

**Step H: workspaces and user management**

- [ ] Clinician sidebar: Dashboard, Patients, Clinical decisions, Follow-ups, Drugs, Diseases, Lab tests, Decision rules — no User management or Clinical rules; no health indicator.
- [ ] As `clinician`, `api http://localhost:8080/api/rules` and `api http://localhost:8080/api/admin/users` → 403; `api http://localhost:8080/api/rules/summaries` → 200 without any `condition` JSON.
- [ ] Admin dashboard shows "Unavailable" when the backend is stopped (reload the page), never "Healthy".
- [ ] Deactivating a signed-in user (second browser) signs them out on their next action with "Your session has ended because your account was changed by an administrator"; changing a role does the same.
- [ ] Account menu opens with Enter/Arrow Down, moves with arrow keys, closes with Escape or a click outside.

**Step G: rules and follow-ups**

- [ ] Activating a rule asks for confirmation and is validated by the backend; deactivating always works; a clinician sees no activate/deactivate buttons and the API returns 403.
- [ ] Builder changes update the JSON and the plain-English line; nested conditions show "edit as JSON".
- [ ] Dry run shows matched / not matched / insufficient data and creates no decision in the history.
- [ ] Follow-up in the past cannot be scheduled; overdue appears under *Overdue* and on the dashboard; completed/cancelled are never overdue; cancelling needs a reason.

**Existing features still work**

- [ ] Patient search, create, edit; vital signs, lab results and conditions; decision and history behave as before step F.
- [ ] Data is still there after restarting the backend.

## 5. Known limitations (by design for this assignment)

- Local HTTP only, so the session cookie is not marked `Secure`. The `dev`
  profile binds the backend to `127.0.0.1`, so nothing travels over the
  network. The settings a future HTTPS deployment would need are in
  [`../backend/README.md`](../backend/README.md) §4 ("HTTP now, HTTPS later").
- **Login attempts are not rate-limited** and accounts are never locked, so
  passwords can be guessed repeatedly (only from this computer, because of
  the loopback binding). This is an unresolved limitation, not a fix.
- No password reset or self-service password change; administrators set an
  initial password when creating an account. Sessions are kept in memory, so a
  backend restart signs everyone out.
- No audit trail: decisions and record changes do not store which user made
  them.
- **Drug-interaction data is a two-row illustrative sample** (Warfarin +
  Ibuprofen, Lisinopril + Ibuprofen), not a licensed or verified source and
  not a drug-interaction database. There is no screen to edit it.
- Allergy matching uses exact drug names and exact knowledge-base categories
  only; there is no ingredient or cross-reactivity data. Free-text allergies
  are flagged as uncertain rather than matched.
- The safety check runs when a medication is added (and is re-run in the
  Medications tab after medication records change); existing medications are
  not re-checked in the background when other records change later. It does not check doses, kidney/liver function, pregnancy or
  conditions.
- Follow-up overdue/upcoming status is calculated when a page loads; there are
  no reminders, notifications or calendar integration.
- Not clinically validated, not production-ready, not for real patient data.
