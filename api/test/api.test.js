// HTTP-level tests with a fake repo, so no Firebase or internet is needed.
const test = require('node:test');
const assert = require('node:assert/strict');
const { createApp } = require('../src/app');
const { QUESTIONS } = require('../src/rules/eligibility');
const { BOOKING_STORE_HOUR_MY } = require('../src/config');

process.env.JOB_SECRET = 'test-secret';
const goodAnswers = Object.fromEntries(QUESTIONS.map((q) => [q.id, q.ok]));
const NOW = new Date('2026-12-01T03:00:00Z'); // 1 Dec 11:00 MYT

function makeFakeRepo(overrides = {}) {
  const users = {
    donor1: { role: 'donor', fullName: 'Donor One', email: 'd1@example.com', bloodType: 'O+' },
    donor2: { role: 'donor', fullName: 'Donor Two', email: 'd2@example.com' },
    staff1: { role: 'staff', centreID: 'C1', fullName: 'Staff One' },
    staff2: { role: ' staff ', centreID: ' C1 ', fullName: 'Staff Two (untrimmed doc)' },
    staffX: { role: 'staff', centreID: 'C2', fullName: 'Other Centre' },
  };
  // Completed donation bookingDates per user: donor2 donated 20 Nov 2026 -> next eligible 15 Jan 2027
  const completed = { donor2: [new Date('2026-11-20T14:30:00+08:00'), new Date('2026-08-01T12:00:00+08:00')] };
  const calls = [];
  const created = [];
  return {
    calls,
    created,
    getUser: async (uid) => users[uid] || null,
    getCentre: async (id) => (id === 'C1' ? { id: 'C1', name: 'Hospital Shah Alam', latitude: 3.0738, longitude: 101.5183 } : null),
    listCompletedDonationDates: async (uid) => [...(completed[uid] || [])],
    getActiveBookingsForUser: async () => [],
    countSlotBookings: async (...a) => { calls.push(['countSlotBookings', ...a]); return 0; },
    createBooking: async (b) => { created.push(b); return 'B1'; },
    getBooking: async (id) =>
      id === 'B1'
        ? { id: 'B1', userID: 'donor1', centreID: 'C1', status: 'Upcoming', checkedIn: false, timeslot: '10:00 AM', bookingDate: new Date('2026-12-05T12:00:00+08:00') }
        : null,
    markCheckedIn: async (...a) => { calls.push(['markCheckedIn', ...a]); },
    setBookingStatus: async (...a) => { calls.push(['setBookingStatus', ...a]); },
    closeBooking: async (...a) => { calls.push(['closeBooking', ...a]); return 'D1'; },
    setStock: async (...a) => { calls.push(['setStock', ...a]); },
    sendTopicPush: async (...a) => { calls.push(['sendTopicPush', ...a]); },
    listBookingsBetween: async () => [],
    listEvents: async () => [
      { id: 'E1', eventDate: '25/11/2026' },
      { id: 'E2', eventDate: '20/12/2026' },
      { id: 'E3', eventDate: '2026-11-30' },
      { id: 'E4', eventDate: 'next week' },
    ],
    deleteEvent: async (id) => { created.push({ deleted: id }); },
    markReminderSent: async (id) => { calls.push(['markReminderSent', id]); },
    ...overrides,
  };
}

async function withServer(repo, fn, { sendReminderEmail = async () => {} } = {}) {
  const app = createApp({
    repo,
    verifyIdToken: async (t) => {
      if (t.startsWith('tok-')) return { uid: t.slice(4) };
      throw new Error('bad token');
    },
    sendReminderEmail,
    now: () => NOW,
  });
  const server = app.listen(0);
  const base = `http://127.0.0.1:${server.address().port}`;
  try {
    await fn(async (path, { token, secret, method = 'GET', body } = {}) => {
      const headers = { 'Content-Type': 'application/json' };
      if (token) headers.Authorization = `Bearer ${token}`;
      if (secret) headers['x-job-secret'] = secret;
      const res = await fetch(base + path, { method, headers, body: body ? JSON.stringify(body) : undefined });
      return { status: res.status, json: await res.json() };
    });
  } finally {
    server.close();
  }
}

const bookBody = { centreId: 'C1', bookingDate: '2026-12-05', timeslot: '10:00 AM', answers: goodAnswers };

// ---------- public / auth ----------

test('GET /health is public', () =>
  withServer(makeFakeRepo(), async (call) => {
    const r = await call('/health');
    assert.equal(r.status, 200);
    assert.equal(r.json.status, 'ok');
  }));

test('no token -> 401, bad token -> 401', () =>
  withServer(makeFakeRepo(), async (call) => {
    assert.equal((await call('/v1/me/eligibility')).status, 401);
    assert.equal((await call('/v1/me/eligibility', { token: 'nope' })).status, 401);
  }));

test('role guard: donor cannot use staff endpoint', () =>
  withServer(makeFakeRepo(), async (call) => {
    const r = await call('/v1/staff/stock', { token: 'tok-donor1', method: 'PUT', body: { bloodType: 'O-', insufficient: true } });
    assert.equal(r.status, 403);
  }));

// ---------- eligibility (computed from Completed donations) ----------

test('me/eligibility: never donated -> eligible now, null date', () =>
  withServer(makeFakeRepo(), async (call) => {
    const r = await call('/v1/me/eligibility', { token: 'tok-donor1' });
    assert.deepEqual(r.json, { nextEligibleDate: null, eligibleNow: true });
  }));

test('me/eligibility: latest Completed donation + 56 days', () =>
  withServer(makeFakeRepo(), async (call) => {
    const r = await call('/v1/me/eligibility', { token: 'tok-donor2' });
    assert.deepEqual(r.json, { nextEligibleDate: '2027-01-15', eligibleNow: false });
  }));

test('eligibility/check passes with good answers', () =>
  withServer(makeFakeRepo(), async (call) => {
    const r = await call('/v1/eligibility/check', { token: 'tok-donor1', method: 'POST', body: { answers: goodAnswers } });
    assert.equal(r.status, 200);
    assert.equal(r.json.eligible, true);
  }));

test('eligibility/check: donor inside wait period is told when they can donate', () =>
  withServer(makeFakeRepo(), async (call) => {
    const r = await call('/v1/eligibility/check', { token: 'tok-donor2', method: 'POST', body: { answers: goodAnswers } });
    assert.equal(r.json.eligible, false);
    assert.equal(r.json.nextEligibleDate, '2027-01-15');
  }));

test('eligibility/check: all failing answers are listed', () =>
  withServer(makeFakeRepo(), async (call) => {
    const answers = { ...goodAnswers, goodHealth: false, recentVaccination: true };
    const r = await call('/v1/eligibility/check', { token: 'tok-donor1', method: 'POST', body: { answers } });
    assert.equal(r.json.eligible, false);
    assert.equal(r.json.reasons.length, 2);
  }));

// ---------- bookings ----------

test('bookings: created (201) as Upcoming for 00:00 MYT of the chosen day', () => {
  const repo = makeFakeRepo();
  return withServer(repo, async (call) => {
    const r = await call('/v1/bookings', { token: 'tok-donor1', method: 'POST', body: bookBody });
    assert.equal(r.status, 201);
    assert.deepEqual(r.json, { bookingId: 'B1', status: 'Upcoming' });
    assert.equal(repo.created[0].userID, 'donor1');
    assert.equal(repo.created[0].bookingDate.toISOString(), '2026-12-04T16:00:00.000Z');
    // slot is counted over the whole Malaysia day, not by == on a timestamp
    const [, centre, dayStart, slot] = repo.calls.find((c) => c[0] === 'countSlotBookings');
    assert.deepEqual([centre, dayStart.toISOString(), slot], ['C1', '2026-12-04T16:00:00.000Z', '10:00 AM']);
  });
});

test('bookings: stored hour falls on the same calendar day in MYT and UTC', () => {
  const dayStart = new Date('2026-12-05T00:00:00+08:00');
  const stored = new Date(dayStart.getTime() + BOOKING_STORE_HOUR_MY * 3600 * 1000);
  assert.equal(stored.toISOString().slice(0, 10), '2026-12-05'); // UTC phone / emulator
  assert.equal(new Date(stored.getTime() + 8 * 3600 * 1000).toISOString().slice(0, 10), '2026-12-05'); // MYT phone
});

test('bookings: donor inside wait period -> 422 NOT_ELIGIBLE', () =>
  withServer(makeFakeRepo(), async (call) => {
    const r = await call('/v1/bookings', { token: 'tok-donor2', method: 'POST', body: bookBody });
    assert.equal(r.status, 422);
    assert.match(r.json.reasons[0], /Too soon/);
  }));

test('bookings: second active booking -> 409', () =>
  withServer(makeFakeRepo({ getActiveBookingsForUser: async () => [{ id: 'X', status: 'Upcoming' }] }), async (call) => {
    const r = await call('/v1/bookings', { token: 'tok-donor1', method: 'POST', body: bookBody });
    assert.equal(r.status, 409);
    assert.equal(r.json.error, 'ACTIVE_BOOKING_EXISTS');
  }));

test('bookings: full slot -> 409 SLOT_FULL', () =>
  withServer(makeFakeRepo({ countSlotBookings: async () => 5 }), async (call) => {
    const r = await call('/v1/bookings', { token: 'tok-donor1', method: 'POST', body: bookBody });
    assert.equal(r.json.error, 'SLOT_FULL');
  }));

// ---------- check-in ----------

test('check-in: at the centre OK (status unchanged), far away refused', () => {
  const repo = makeFakeRepo();
  return withServer(repo, async (call) => {
    const ok = await call('/v1/bookings/B1/check-in', { token: 'tok-donor1', method: 'POST', body: { lat: 3.0739, lng: 101.5184 } });
    assert.equal(ok.status, 200);
    assert.equal(ok.json.checkedIn, true);
    assert.equal(ok.json.status, 'Upcoming');
    assert.deepEqual(repo.calls.find((c) => c[0] === 'markCheckedIn'), ['markCheckedIn', 'B1', 3.0739, 101.5184]);
    const far = await call('/v1/bookings/B1/check-in', { token: 'tok-donor1', method: 'POST', body: { lat: 3.1, lng: 101.6 } });
    assert.equal(far.status, 403);
    assert.equal(far.json.error, 'NOT_AT_CENTRE');
  });
});

test('check-in: another donor cannot check in to my booking', () =>
  withServer(makeFakeRepo(), async (call) => {
    const r = await call('/v1/bookings/B1/check-in', { token: 'tok-donor2', method: 'POST', body: { lat: 3.0739, lng: 101.5184 } });
    assert.equal(r.status, 404);
  }));

test('check-in: already checked in -> 409', () =>
  withServer(
    makeFakeRepo({ getBooking: async () => ({ id: 'B1', userID: 'donor1', centreID: 'C1', status: 'Upcoming', checkedIn: true }) }),
    async (call) => {
      const r = await call('/v1/bookings/B1/check-in', { token: 'tok-donor1', method: 'POST', body: { lat: 3.0739, lng: 101.5184 } });
      assert.equal(r.status, 409);
      assert.equal(r.json.error, 'ALREADY_CHECKED_IN');
    }
  ));

// ---------- staff status ----------

test('staff: Eligible updates the booking in place (no move to donation)', () => {
  const repo = makeFakeRepo();
  return withServer(repo, async (call) => {
    const r = await call('/v1/staff/bookings/B1/status', { token: 'tok-staff1', method: 'PATCH', body: { status: 'Eligible' } });
    assert.equal(r.status, 200);
    assert.deepEqual(r.json, { bookingId: 'B1', donationId: null, status: 'Eligible', nextEligibleDate: null });
    assert.deepEqual(repo.calls.find((c) => c[0] === 'setBookingStatus'), ['setBookingStatus', 'B1', 'Eligible']);
    assert.equal(repo.calls.some((c) => c[0] === 'closeBooking'), false);
  });
});

test('staff: Completed moves to donation; next eligible = booking day + 56', () => {
  const repo = makeFakeRepo();
  return withServer(repo, async (call) => {
    const r = await call('/v1/staff/bookings/B1/status', { token: 'tok-staff1', method: 'PATCH', body: { status: 'Completed' } });
    assert.equal(r.status, 200);
    assert.equal(r.json.donationId, 'D1');
    assert.equal(r.json.nextEligibleDate, '2027-01-30'); // 5 Dec 2026 + 56 days
    assert.deepEqual(repo.calls.find((c) => c[0] === 'closeBooking'), ['closeBooking', 'B1', 'Completed']);
  });
});

test('staff: Absent closes without changing the next eligible date', () =>
  withServer(makeFakeRepo(), async (call) => {
    const r = await call('/v1/staff/bookings/B1/status', { token: 'tok-staff1', method: 'PATCH', body: { status: 'Absent' } });
    assert.equal(r.status, 200);
    assert.equal(r.json.nextEligibleDate, null); // donor1 has no Completed donations
  }));

test('staff: bad status 400, Eligible -> Eligible 409', () =>
  withServer(
    makeFakeRepo({ getBooking: async () => ({ id: 'B2', userID: 'donor1', centreID: 'C1', status: 'Eligible', bookingDate: NOW }) }),
    async (call) => {
      const bad = await call('/v1/staff/bookings/B2/status', { token: 'tok-staff1', method: 'PATCH', body: { status: 'CheckedIn' } });
      assert.equal(bad.status, 400);
      const again = await call('/v1/staff/bookings/B2/status', { token: 'tok-staff1', method: 'PATCH', body: { status: 'Eligible' } });
      assert.equal(again.status, 409);
      assert.equal(again.json.error, 'BAD_TRANSITION');
    }
  ));

test('staff: other centre is forbidden, untrimmed role/centreID still works', () =>
  withServer(makeFakeRepo(), async (call) => {
    const other = await call('/v1/staff/bookings/B1/status', { token: 'tok-staffX', method: 'PATCH', body: { status: 'Completed' } });
    assert.equal(other.status, 403);
    const trimmed = await call('/v1/staff/bookings/B1/status', { token: 'tok-staff2', method: 'PATCH', body: { status: 'Eligible' } });
    assert.equal(trimmed.status, 200);
  }));

// ---------- stock ----------

test('staff: stock update validates input, uses trimmed centreID for doc and topic', () => {
  const repo = makeFakeRepo();
  return withServer(repo, async (call) => {
    const bad = await call('/v1/staff/stock', { token: 'tok-staff2', method: 'PUT', body: { bloodType: 'Z', insufficient: true } });
    assert.equal(bad.status, 400);
    const ok = await call('/v1/staff/stock', { token: 'tok-staff2', method: 'PUT', body: { bloodType: 'O-', insufficient: true } });
    assert.equal(ok.status, 200);
    assert.equal(ok.json.pushed, true);
    assert.deepEqual(repo.calls.find((c) => c[0] === 'setStock'), ['setStock', 'C1', 'O-', true]);
    assert.equal(repo.calls.find((c) => c[0] === 'sendTopicPush')[1], 'centre_C1');
  });
});

// ---------- jobs ----------

test('jobs: need the secret', () =>
  withServer(makeFakeRepo(), async (call) => {
    assert.equal((await call('/v1/jobs/expire-announcements', { method: 'POST' })).status, 401);
    assert.equal((await call('/v1/jobs/expire-announcements', { method: 'POST', secret: 'wrong' })).status, 401);
  }));

test('jobs: expire-announcements deletes only past events, keeps unparseable', () => {
  const repo = makeFakeRepo();
  return withServer(repo, async (call) => {
    const r = await call('/v1/jobs/expire-announcements', { method: 'POST', secret: 'test-secret' });
    assert.deepEqual(r.json, { checked: 4, deleted: 2 });
    assert.deepEqual(repo.created, [{ deleted: 'E1' }, { deleted: 'E3' }]);
  });
});

test('jobs: send-reminders emails once per active booking with the app template params', () => {
  const sent = [];
  const tomorrow = new Date('2026-12-02T12:00:00+08:00');
  const repo = makeFakeRepo({
    listBookingsBetween: async () => [
      { id: 'B1', userID: 'donor1', centreID: 'C1', status: 'Upcoming', timeslot: '09:00 AM', bookingDate: tomorrow },
      { id: 'B2', userID: 'donor1', centreID: 'C1', status: 'Upcoming', reminderSent: true, timeslot: '09:00 AM', bookingDate: tomorrow },
      { id: 'B3', userID: 'donor1', centreID: 'C1', status: 'Completed', timeslot: '09:00 AM', bookingDate: tomorrow },
    ],
  });
  return withServer(
    repo,
    async (call) => {
      const r = await call('/v1/jobs/send-reminders', { method: 'POST', secret: 'test-secret' });
      assert.deepEqual(r.json, { checked: 3, sent: 1, failed: 0 });
      assert.deepEqual(sent, [
        { email: 'd1@example.com', name: 'Donor One', centre: 'Hospital Shah Alam', date: '2026-12-02', time: '09:00 AM' },
      ]);
      assert.deepEqual(repo.calls.filter((c) => c[0] === 'markReminderSent'), [['markReminderSent', 'B1']]);
    },
    { sendReminderEmail: async (m) => { sent.push(m); } }
  );
});

test('jobs: reminder window is tomorrow in Malaysia time', () => {
  let win;
  const repo = makeFakeRepo({ listBookingsBetween: async (s, e) => { win = [s.toISOString(), e.toISOString()]; return []; } });
  return withServer(repo, async (call) => {
    await call('/v1/jobs/send-reminders', { method: 'POST', secret: 'test-secret' });
    // NOW = 1 Dec 11:00 MYT -> window is 2 Dec 00:00 to 3 Dec 00:00 MYT
    assert.deepEqual(win, ['2026-12-01T16:00:00.000Z', '2026-12-02T16:00:00.000Z']);
  });
});
