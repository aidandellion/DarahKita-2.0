# DarahKita API contract (v1)

Base URL: `https://darahkita-api.onrender.com/v1`  (health check: `GET /health`, no `/v1`)

All requests and responses are JSON. Dates in requests and responses are `YYYY-MM-DD` in Malaysia time. Errors always look like:

```json
{ "error": "SLOT_FULL", "message": "This time slot is full" }
```

## Getting started (for the team)

**Live API:** https://darahkita-api.onrender.com  (health check: `GET /health`)
**Base URL for all endpoints:** `https://darahkita-api.onrender.com/v1`
**Code:** the `api/` folder of this repo. **Hosting:** Render (free plan). **Scheduler:** GitHub Actions (`.github/workflows/scheduler.yml`, daily 09:17 MYT).

### Cold start
The free host sleeps after about 15 minutes idle. The first call after that can take 30–60 seconds, and later calls take well under a second. In the app, use 60 s timeouts and show a loading indicator. In Postman, set the request timeout to at least 60 s, or call `GET /health` first to wake the service.

### Get a token to test with
Every donor and staff call needs a Firebase ID token (valid about 1 hour). Log in with a test account using the project's Firebase Web API key (shared in the group chat, not in this repo).

PowerShell:
```powershell
$key = "FIREBASE_WEB_API_KEY"
$r = Invoke-RestMethod -Method Post -Uri "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$key" -ContentType "application/json" -Body '{"email":"EMAIL","password":"PASSWORD","returnSecureToken":true}'
$t = $r.idToken
```

Then call an endpoint:
```powershell
Invoke-RestMethod -Uri "https://darahkita-api.onrender.com/v1/me/eligibility" -Headers @{Authorization="Bearer $t"}
```

In Postman, send the same login request as a POST, copy `idToken` from the response, and set `Authorization: Bearer <idToken>` on later requests. Store the base URL and token as collection variables.

### Test accounts
A donor account and a staff account are shared in the group chat. Staff accounts can only change bookings at their own centre (`403 FORBIDDEN – Different centre` otherwise). A donor can only use their own bookings (`404 BOOKING_NOT_FOUND` for someone else's).

### Reading errors in PowerShell 5.1
PowerShell hides error bodies. Use this to see the status and the JSON message:
```powershell
try { <your Invoke-RestMethod call> } catch { $_.Exception.Response.StatusCode.value__; (New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())).ReadToEnd() }
```

### Who uses what
- **B (app):** endpoints 2–7. The eligibility screen is already refactored on branch `feature/api-eligibility`, so follow the same pattern (`app/.../api/` package: `ApiClient`, `AuthInterceptor`, `DarahKitaApi`).
- **C (staff, jobs, security):** endpoints 6–9, EmailJS server-side variables on Render, and the Firestore security rules.
- **D (testing, report):** Postman collection from this contract and the report diagrams.

### Verified live (20 Nov 2026)
Eligibility check, booking creation (including double-booking 409), check-in (`NOT_AT_CENTRE` and success), staff status, staff stock, and the scheduler workflow. Not yet verified live: the reminder email (EmailJS is not configured on the server yet).

### Rules for everyone
- Never commit `google-services.json`, `local.properties`, `.env` files or any service account key.
- Announce contract changes in the group chat before editing this file.

## Authentication

Donor and staff endpoints need the Firebase ID token of the logged-in user:

```
Authorization: Bearer <Firebase ID token>
```

The API reads `role` (`donor` / `staff`) and, for staff, `centreID` from the user's `users` document. Both are trimmed. Donors can only call donor endpoints, staff only staff endpoints. Job endpoints use `x-job-secret: <secret>` instead (called by the scheduler, not the app).

## Data rules

- **Booking statuses.** Active: `Upcoming` (new booking), `Pending` (also used for a missing status), `Eligible` (staff passed the donor). Closed, in `donation`: `Completed`, `Ineligible`, `Absent`.
- **Check-in** sets `checkedIn: true` and `checkinLocation` only. The status is not changed.
- **Next eligible date** is not a stored field. It is the latest `Completed` `donation.bookingDate` for the donor + 56 days (`WAIT_DAYS`), counted in Malaysia days. `null` means never donated.
- **Slot capacity:** 5 active bookings per centre, day and timeslot (`SLOT_CAPACITY`).
- **New booking document** (`booking`): `userID`, `centreID`, `bookingDate` (Timestamp, 12:00 MYT of the chosen day), `timeslot`, `status: "Upcoming"`, `checkedIn: false`, `checkinLocation: null`, `reminderSent: false`. There is no `bookingID` field; the document id is the booking id.

## Endpoints

| # | Method and path | Who | Purpose |
|---|---|---|---|
| 1 | `GET /health` | public | Is the API up (also wakes the free host) |
| 2 | `GET /v1/me/eligibility` | donor | Next eligible date and whether the donor can donate now |
| 3 | `POST /v1/eligibility/check` | donor | Run the questionnaire and the 56-day rule |
| 4 | `POST /v1/bookings` | donor | Validate and create a booking |
| 5 | `POST /v1/bookings/:id/check-in` | donor | GPS check-in with distance check |
| 6 | `PATCH /v1/staff/bookings/:id/status` | staff | Mark Eligible, or close as Completed / Ineligible / Absent |
| 7 | `PUT /v1/staff/stock` | staff | Mark a blood type sufficient or insufficient, push alert |
| 8 | `POST /v1/jobs/send-reminders` | scheduler | Email donors booked for tomorrow |
| 9 | `POST /v1/jobs/expire-announcements` | scheduler | Delete announcements whose event date has passed |

## Details

### 2. GET /v1/me/eligibility
200: `{ "nextEligibleDate": "2027-01-15" | null, "eligibleNow": true }`

### 3. POST /v1/eligibility/check
All 9 answers are required (`true` = Yes, `false` = No). The answer shown makes the donor eligible:

| id | Question | Eligible answer |
|---|---|---|
| `goodHealth` | Are you currently in good health? | `true` |
| `ageValid` | Are you aged between 18 and 60 years old? | `true` |
| `weightValid` | Do you weigh at least 45 kg? | `true` |
| `recentDonation` | Have you donated blood in the last 56 days? | `false` |
| `onMedication` | Are you currently taking any prescribed medication or antibiotics? | `false` |
| `recentVaccination` | Have you received any vaccination in the past 4 weeks? | `false` |
| `recentTattooOrSurgery` | Tattoo, piercing, or any surgery/dental procedure in the last 6 months? | `false` |
| `pregnantOrBreastfeeding` | Pregnant, breastfeeding, or given birth in the last 6 months? | `false` |
| `chronicIllness` | Any chronic illness (e.g. diabetes, heart disease, hepatitis, HIV)? | `false` |

Request: `{ "answers": { "goodHealth": true, "ageValid": true, "weightValid": true, "recentDonation": false, "onMedication": false, "recentVaccination": false, "recentTattooOrSurgery": false, "pregnantOrBreastfeeding": false, "chronicIllness": false } }`

200: `{ "eligible": false, "reasons": ["Too soon since last donation (12 day(s) left)", "Not eligible: Do you weigh at least 45 kg?"], "nextEligibleDate": "2027-01-15" }`

Every failing reason is returned, not just the first.

### 4. POST /v1/bookings
Request: `{ "centreId": "C001", "bookingDate": "2026-12-05", "timeslot": "10:00 AM", "answers": { ... } }`
201: `{ "bookingId": "abc123", "status": "Upcoming" }`

| Status | error | Meaning |
|---|---|---|
| 400 | BAD_REQUEST | Missing or badly formatted field |
| 404 | CENTRE_NOT_FOUND | Unknown centre |
| 409 | PAST_DATE | Date already passed |
| 409 | NOT_ELIGIBLE_YET | Date is before the next eligible date |
| 409 | ACTIVE_BOOKING_EXISTS | Donor already has an active (Upcoming / Pending / Eligible) booking |
| 409 | SLOT_FULL | Slot at capacity |
| 422 | NOT_ELIGIBLE | Questionnaire or 56-day rule failed (see `reasons`) |

### 5. POST /v1/bookings/:id/check-in
Request: `{ "lat": 3.0739, "lng": 101.5184 }`
200: `{ "checkedIn": true, "status": "Upcoming", "distanceMetres": 14 }` (status is unchanged)
403 `NOT_AT_CENTRE` (includes `distanceMetres`), 400 `BAD_COORDS`, 404 `BOOKING_NOT_FOUND`, 409 `BAD_STATUS` (booking not active), 409 `ALREADY_CHECKED_IN`

The distance uses the centre's `latitude` / `longitude` and must be within `CHECKIN_RADIUS_M` (default 100 m).

### 6. PATCH /v1/staff/bookings/:id/status
Request: `{ "status": "Eligible" | "Completed" | "Ineligible" | "Absent" }`

- `Eligible`: only from `Upcoming` / `Pending`. The booking stays in `booking`; only `status` changes.
- `Completed` / `Ineligible` / `Absent`: only from `Upcoming` / `Pending` / `Eligible`. Every booking field is copied to `donation` with the final `status`, `bookingID` (the booking doc id), `recordedAt` and the donor's `bloodType` (`"N/A"` if unknown). Then the booking is deleted.

200: `{ "bookingId": "abc123", "donationId": "d1" | null, "status": "Completed", "nextEligibleDate": "2027-01-30" | null }`

`nextEligibleDate` is the donor's latest Completed donation + 56 days (this booking counts if it was just Completed). It is `null` if the donor has never completed one.
400 `BAD_STATUS`, 409 `BAD_TRANSITION`, 404 `BOOKING_NOT_FOUND`, 403 `FORBIDDEN` if the booking belongs to another centre.

### 7. PUT /v1/staff/stock
Request: `{ "bloodType": "O-", "insufficient": true }` (bloodType is one of `A+ A- B+ B- AB+ AB- O+ O-`)
200: `{ "bloodType": "O-", "insufficient": true, "pushed": true }`

Writes `bloodStock/{centreID}_{OMinus}` (same doc id as the app) with merge: `centreID`, `bloodType`, `outStock` (= insufficient), `lastUpdated`. When insufficient, it sends a push to topic `centre_{centreID}`.

### 8 and 9. Jobs
`POST /v1/jobs/send-reminders` → `{ "checked": 4, "sent": 3, "failed": 0 }`
Emails each active booking for tomorrow (Malaysia time) once (`reminderSent`). EmailJS template params: `email`, `name`, `centre`, `date` (`yyyy-MM-dd`), `time` (e.g. `09:00 AM`).

`POST /v1/jobs/expire-announcements` → `{ "checked": 6, "deleted": 2 }`
`event.eventDate` is read as `dd/MM/yyyy` (or `yyyy-MM-dd`). An event is deleted from 00:00 MYT the day after its date. Unparseable dates are never deleted.
