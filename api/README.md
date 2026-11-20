# DarahKita API

In-house REST API for the DarahKita Android app. Lives in the `api/` folder of the DarahKita-2.0 repo. Node.js + Express, Firebase Admin SDK, deployed on Render (free), scheduled jobs on GitHub Actions.

Endpoint contract: [docs/API.md](docs/API.md)

## Run locally

```bash
cd api
npm install
cp .env.example .env      # then fill in the values
# load .env however you like, e.g. Node 20+:
node --env-file=.env src/index.js
curl http://localhost:3000/health
```

## Tests

```bash
npm test      # unit tests + HTTP tests with a fake repo, no Firebase needed
```

## Layout

```
src/rules/     pure business rules (eligibility, booking, check-in, announcements) - unit tested
src/routes/    Express routes (donor, staff, jobs)
src/repo.js    the only file that talks to Firestore
src/auth.js    Firebase token check, role guard, job secret
(repo root) .github/workflows/scheduler.yml   daily reminder + announcement-expiry trigger
(repo root) .github/workflows/api-ci.yml      runs the API unit tests on every push
(repo root) render.yaml                       optional Render Blueprint (rootDir: api)
```

## Environment variables

See `.env.example`. On Render set them under Dashboard > Environment (`render.yaml` lists them).

| Variable | Purpose |
|---|---|
| `FIREBASE_SERVICE_ACCOUNT` | Whole service account JSON on one line. Never commit it. |
| `JOB_SECRET` | Shared secret for `/v1/jobs/*` (sent by GitHub Actions as `x-job-secret`) |
| `WAIT_DAYS`, `CHECKIN_RADIUS_M`, `SLOT_CAPACITY` | Rules (defaults 56, 100, 5) |
| `EMAILJS_REMINDER_SERVICE_ID`, `EMAILJS_REMINDER_TEMPLATE_ID` | The reminder service/template the app already uses |
| `EMAILJS_PUBLIC_KEY`, `EMAILJS_PRIVATE_KEY` | EmailJS account keys (private key = server only) |

## Aligned with the app

Checked against the Android code (`app/src/main/java/...`):

1. **Collections:** `users`, `booking`, `donation`, `centre`, `bloodStock`, `event`.
2. **Eligibility:** the 9 questions from `EligibilityScreeningActivity` (same ids and disqualifying answers). The API returns every failing reason.
3. **Statuses:** active bookings are `Upcoming`, `Pending` (also used when status is missing) or `Eligible`. Closed records in `donation` are `Completed`, `Ineligible` or `Absent`. Check-in sets only `checkedIn` + `checkinLocation`, never the status.
4. **Next eligible date:** not stored. It is the latest `Completed` `donation.bookingDate` for the donor + `WAIT_DAYS`, counted in Malaysia days (like the app).
5. **bookingDate:** the API stores 12:00 Malaysia time of the chosen day, so the app's "today" queries find it on MYT and UTC devices. Slot counting reads the whole Malaysia day (00:00-24:00) and filters centre/timeslot/status in code.
6. **bloodStock:** doc id `{centreID}_{APlus|AMinus|BPlus|...}` like `StockActivity`, written with merge.
7. **event.eventDate:** `dd/MM/yyyy` from the app (also accepts `yyyy-MM-dd`). Expires the day after; unparseable dates are never deleted.
8. **Reminder email params:** `email`, `name`, `centre`, `date`, `time`, same as `EmailHelper.sendReminder`.
9. **role / centreID** on `users` are trimmed by the API before use.

Firestore queries use a single field each, so no composite indexes are needed.

## Known gaps on the app side (not changed here)

- The app still writes bookings, check-ins, status and stock directly to Firestore and still schedules its own reminder (AlarmManager). Until those move to the API, app-created bookings can get two reminders (they have no `reminderSent` field).
- The app does not subscribe to any FCM topic yet, so the `centre_{centreID}` stock push reaches nobody until it does.
- The app's check-in radius is 200 m; the API default is 100 m (`CHECKIN_RADIUS_M`).
