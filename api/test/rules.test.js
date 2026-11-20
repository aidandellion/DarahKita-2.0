const test = require('node:test');
const assert = require('node:assert/strict');
const { checkEligibility, nextEligibleDate, nextEligibleFromDonations, QUESTIONS } = require('../src/rules/eligibility');
const { verifyCheckIn, distanceMetres } = require('../src/rules/checkin');
const {
  validateNewBooking,
  parseBookingDate,
  normaliseStatus,
  isActiveStatus,
  countSlot,
  validateStatusChange,
} = require('../src/rules/booking');
const { isExpired, parseEventDate } = require('../src/rules/announcements');
const { stockDocId, isBloodType } = require('../src/rules/stock');
const { myDayStart, toMyDateString } = require('../src/rules/dates');

const goodAnswers = Object.fromEntries(QUESTIONS.map((q) => [q.id, q.ok]));
const NOW = new Date('2026-12-01T03:00:00Z'); // 1 Dec 11:00 MYT

// ---------- eligibility ----------

test('eligibility: the 9 question ids and disqualifying answers match the app', () => {
  assert.deepEqual(
    QUESTIONS.map((q) => [q.id, q.ok]),
    [
      ['goodHealth', true],
      ['ageValid', true],
      ['weightValid', true],
      ['recentDonation', false],
      ['onMedication', false],
      ['recentVaccination', false],
      ['recentTattooOrSurgery', false],
      ['pregnantOrBreastfeeding', false],
      ['chronicIllness', false],
    ]
  );
});

test('eligibility: first-time donor with good answers is eligible', () => {
  const r = checkEligibility({ answers: goodAnswers, nextEligible: null, now: NOW });
  assert.equal(r.eligible, true);
  assert.deepEqual(r.reasons, []);
});

test('eligibility: "No" to good health makes donor ineligible', () => {
  const r = checkEligibility({ answers: { ...goodAnswers, goodHealth: false }, nextEligible: null, now: NOW });
  assert.equal(r.eligible, false);
  assert.match(r.reasons[0], /good health/i);
});

test('eligibility: "Yes" to chronic illness makes donor ineligible', () => {
  const r = checkEligibility({ answers: { ...goodAnswers, chronicIllness: true }, nextEligible: null, now: NOW });
  assert.equal(r.eligible, false);
  assert.match(r.reasons[0], /chronic illness/i);
});

test('eligibility: returns every failing reason, not just the first', () => {
  const r = checkEligibility({
    answers: { ...goodAnswers, weightValid: false, onMedication: true, recentTattooOrSurgery: true },
    nextEligible: new Date('2026-12-20T00:00:00+08:00'),
    now: NOW,
  });
  assert.equal(r.reasons.length, 4);
  assert.match(r.reasons[0], /Too soon/);
});

test('eligibility: missing answer is rejected', () => {
  const { ageValid, ...partial } = goodAnswers;
  const r = checkEligibility({ answers: partial, nextEligible: null, now: NOW });
  assert.equal(r.eligible, false);
  assert.ok(r.reasons.includes('Missing answer: ageValid'));
});

test('eligibility: still inside 56-day wait', () => {
  const next = new Date('2026-12-20T00:00:00Z');
  const r = checkEligibility({ answers: goodAnswers, nextEligible: next, now: NOW });
  assert.equal(r.eligible, false);
  assert.match(r.reasons[0], /Too soon/);
});

test('eligibility: exactly on the next eligible date is allowed', () => {
  const r = checkEligibility({ answers: goodAnswers, nextEligible: NOW, now: NOW });
  assert.equal(r.eligible, true);
});

test('nextEligibleDate adds 56 Malaysia days and drops the time of day', () => {
  // 1 Jan 2026 14:30 MYT -> 26 Feb 2026 00:00 MYT
  const next = nextEligibleDate(new Date('2026-01-01T14:30:00+08:00'));
  assert.equal(next.toISOString(), '2026-02-25T16:00:00.000Z');
  assert.equal(toMyDateString(next), '2026-02-26');
});

test('nextEligibleFromDonations: uses the latest Completed date, null when none', () => {
  assert.equal(nextEligibleFromDonations([]), null);
  assert.equal(nextEligibleFromDonations(undefined), null);
  const next = nextEligibleFromDonations([
    new Date('2026-03-01T12:00:00+08:00'),
    new Date('2026-06-10T09:00:00+08:00'),
    null,
    new Date('2026-05-01T12:00:00+08:00'),
  ]);
  assert.equal(toMyDateString(next), '2026-08-05');
});

// ---------- dates ----------

test('dates: Malaysia day start and date string', () => {
  // 31 Dec 2026 20:00 UTC = 1 Jan 2027 04:00 MYT
  const t = new Date('2026-12-31T20:00:00Z');
  assert.equal(toMyDateString(t), '2027-01-01');
  assert.equal(myDayStart(t).toISOString(), '2026-12-31T16:00:00.000Z');
  assert.equal(myDayStart(t, 1).toISOString(), '2027-01-01T16:00:00.000Z');
});

// ---------- check-in ----------

test('check-in: inside radius passes, outside fails', () => {
  const centre = { centreLat: 3.0738, centreLng: 101.5183 };
  assert.equal(verifyCheckIn({ userLat: 3.0739, userLng: 101.5184, ...centre }).ok, true);
  const far = verifyCheckIn({ userLat: 3.0838, userLng: 101.5183, ...centre }); // ~1.1 km north
  assert.equal(far.ok, false);
  assert.equal(far.code, 'NOT_AT_CENTRE');
  assert.ok(far.distance > 1000 && far.distance < 1200);
});

test('check-in: bad coordinates rejected', () => {
  assert.equal(verifyCheckIn({ userLat: 'x', userLng: 1, centreLat: 1, centreLng: 1 }).code, 'BAD_COORDS');
  assert.equal(verifyCheckIn({ userLat: 1, userLng: 1, centreLat: undefined, centreLng: 1 }).code, 'BAD_COORDS');
});

test('distance: same point is zero', () => {
  assert.equal(distanceMetres(3, 101, 3, 101), 0);
});

// ---------- booking ----------

test('parseBookingDate: valid and invalid', () => {
  assert.equal(parseBookingDate('2026-12-05').toISOString(), '2026-12-04T16:00:00.000Z'); // 00:00 MYT
  assert.equal(parseBookingDate('05/12/2026'), null);
  assert.equal(parseBookingDate('2026-02-31'), null);
  assert.equal(parseBookingDate(undefined), null);
});

test('status: missing or blank is Pending; active = Upcoming, Pending, Eligible', () => {
  assert.equal(normaliseStatus(undefined), 'Pending');
  assert.equal(normaliseStatus('  '), 'Pending');
  assert.equal(normaliseStatus('Eligible '), 'Eligible');
  for (const s of ['Upcoming', 'Pending', 'Eligible', null]) assert.equal(isActiveStatus(s), true, String(s));
  for (const s of ['Completed', 'Ineligible', 'Absent', 'Booked']) assert.equal(isActiveStatus(s), false, s);
});

const base = {
  bookingDate: parseBookingDate('2026-12-05'),
  timeslot: '10:00 AM',
  nextEligible: null,
  activeBookingCount: 0,
  slotCount: 0,
  slotCapacity: 5,
  now: NOW,
};

test('booking: happy path', () => assert.equal(validateNewBooking(base).ok, true));

test('booking: past date', () => {
  const r = validateNewBooking({ ...base, bookingDate: parseBookingDate('2026-11-20') });
  assert.equal(r.code, 'PAST_DATE');
});

test('booking: today is still allowed', () => {
  assert.equal(validateNewBooking({ ...base, bookingDate: parseBookingDate('2026-12-01') }).ok, true);
});

test('booking: before next eligible date', () => {
  const r = validateNewBooking({ ...base, nextEligible: new Date('2026-12-10T00:00:00+08:00') });
  assert.equal(r.code, 'NOT_ELIGIBLE_YET');
  assert.match(r.message, /2026-12-10/);
});

test('booking: second active booking blocked', () => {
  assert.equal(validateNewBooking({ ...base, activeBookingCount: 1 }).code, 'ACTIVE_BOOKING_EXISTS');
});

test('booking: full slot blocked, one below capacity allowed', () => {
  assert.equal(validateNewBooking({ ...base, slotCount: 5 }).code, 'SLOT_FULL');
  assert.equal(validateNewBooking({ ...base, slotCount: 4 }).ok, true);
});

test('countSlot: same centre + timeslot + active status only, any time of day', () => {
  const day = [
    { centreID: 'C001', timeslot: '10:00 AM', status: 'Upcoming', bookingDate: new Date('2026-12-05T12:00:00+08:00') },
    { centreID: 'C001 ', timeslot: '10:00 AM', status: 'Eligible', bookingDate: new Date('2026-12-05T15:42:10+08:00') },
    { centreID: 'C001', timeslot: '10:00 AM', bookingDate: new Date('2026-12-05T08:00:00+08:00') }, // no status = Pending
    { centreID: 'C001', timeslot: '10:00 AM', status: 'Completed' },
    { centreID: 'C001', timeslot: '11:00 AM', status: 'Upcoming' },
    { centreID: 'C002', timeslot: '10:00 AM', status: 'Upcoming' },
  ];
  assert.equal(countSlot(day, 'C001', '10:00 AM'), 3);
  assert.equal(countSlot([], 'C001', '10:00 AM'), 0);
});

test('status change: Eligible only from Upcoming/Pending', () => {
  assert.deepEqual(validateStatusChange('Upcoming', 'Eligible'), { ok: true, close: false });
  assert.deepEqual(validateStatusChange(undefined, 'Eligible'), { ok: true, close: false });
  assert.equal(validateStatusChange('Eligible', 'Eligible').code, 'BAD_TRANSITION');
});

test('status change: close only from Upcoming/Pending/Eligible, unknown target rejected', () => {
  for (const from of ['Upcoming', 'Pending', 'Eligible']) {
    for (const to of ['Completed', 'Ineligible', 'Absent']) {
      assert.deepEqual(validateStatusChange(from, to), { ok: true, close: true }, `${from} -> ${to}`);
    }
  }
  assert.equal(validateStatusChange('Completed', 'Absent').code, 'BAD_TRANSITION');
  assert.equal(validateStatusChange('Upcoming', 'CheckedIn').code, 'BAD_STATUS');
  assert.equal(validateStatusChange('Upcoming', undefined).code, 'BAD_STATUS');
});

// ---------- stock ----------

test('stock: deterministic doc id matches StockActivity', () => {
  assert.equal(stockDocId('C001', 'A+'), 'C001_APlus');
  assert.equal(stockDocId('C001', 'AB-'), 'C001_ABMinus');
  assert.equal(stockDocId('C001', 'O-'), 'C001_OMinus');
  assert.equal(isBloodType('B+'), true);
  assert.equal(isBloodType('BPlus'), false);
});

// ---------- announcements ----------

test('announcements: parses dd/MM/yyyy (app format) and yyyy-MM-dd', () => {
  assert.equal(toMyDateString(parseEventDate('05/12/2026')), '2026-12-05');
  assert.equal(toMyDateString(parseEventDate('2026-12-05')), '2026-12-05');
  assert.equal(parseEventDate('31/02/2026'), null);
  assert.equal(parseEventDate('12-05-2026'), null);
  assert.equal(parseEventDate(null), null);
});

test('announcements: expires the day after eventDate (dd/MM/yyyy)', () => {
  assert.equal(isExpired('30/11/2026', NOW), true);
  assert.equal(isExpired('01/12/2026', NOW), false); // event is today
  assert.equal(isExpired('10/12/2026', NOW), false);
  // boundary: 2 Dec 00:00 MYT is the first moment after the event day
  assert.equal(isExpired('01/12/2026', new Date('2026-12-01T15:59:59Z')), false);
  assert.equal(isExpired('01/12/2026', new Date('2026-12-01T16:00:00Z')), true);
});

test('announcements: yyyy-MM-dd still works, unknown formats are never deleted', () => {
  assert.equal(isExpired('2026-11-30', NOW), true);
  assert.equal(isExpired('2026-12-01', NOW), false);
  assert.equal(isExpired('tomorrow-ish', NOW), false);
  assert.equal(isExpired('٣٠/١١/٢٠٢٦', NOW), false); // non-Latin digits from another locale
  assert.equal(isExpired(undefined, NOW), false);
});
