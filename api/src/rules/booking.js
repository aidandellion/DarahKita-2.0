const { SLOT_CAPACITY, ACTIVE_STATUSES, CLOSING_STATUSES, STATUS } = require('../config');
const { myDateFromParts, toMyDateString } = require('./dates');

/** "2026-12-01" -> Date at 00:00 Malaysia time. Returns null if the format or day is wrong. */
function parseBookingDate(str) {
  if (typeof str !== 'string') return null;
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(str);
  return m ? myDateFromParts(+m[1], +m[2], +m[3]) : null;
}

/** The app shows a missing/blank status as "Pending" (QueueAdapter), so the API does too. */
function normaliseStatus(status) {
  return typeof status === 'string' && status.trim() !== '' ? status.trim() : STATUS.PENDING;
}

const isActiveStatus = (status) => ACTIVE_STATUSES.includes(normaliseStatus(status));

/**
 * Pure booking rules. Returns { ok: true } or { ok: false, code, message }.
 * @param {object} p
 * @param {Date} p.bookingDate 00:00 MYT of the chosen day
 * @param {string} p.timeslot
 * @param {Date|null} p.nextEligible donor's next eligible date
 * @param {number} p.activeBookingCount donor's current active bookings
 * @param {number} p.slotCount active bookings already in this centre/day/timeslot
 * @param {Date} [p.now]
 */
function validateNewBooking({
  bookingDate,
  timeslot,
  nextEligible,
  activeBookingCount,
  slotCount,
  slotCapacity = SLOT_CAPACITY,
  now = new Date(),
}) {
  if (!bookingDate || !timeslot) {
    return { ok: false, code: 'BAD_REQUEST', message: 'bookingDate and timeslot are required' };
  }
  if (bookingDate.getTime() + 24 * 3600 * 1000 <= now.getTime()) {
    return { ok: false, code: 'PAST_DATE', message: 'Booking date is in the past' };
  }
  if (nextEligible && bookingDate < nextEligible) {
    return {
      ok: false,
      code: 'NOT_ELIGIBLE_YET',
      message: `Not eligible until ${toMyDateString(nextEligible)}`,
    };
  }
  if (activeBookingCount > 0) {
    return { ok: false, code: 'ACTIVE_BOOKING_EXISTS', message: 'You already have an active booking' };
  }
  if (slotCount >= slotCapacity) {
    return { ok: false, code: 'SLOT_FULL', message: 'This time slot is full' };
  }
  return { ok: true };
}

/** Active bookings in one centre + timeslot, from a list of that day's bookings (filtered in code, no index). */
function countSlot(dayBookings, centreID, timeslot) {
  return (dayBookings || []).filter(
    (b) => String(b.centreID || '').trim() === centreID && b.timeslot === timeslot && isActiveStatus(b.status)
  ).length;
}

/**
 * Staff status change, mirroring the app's queue:
 * - Eligible: only from Upcoming / Pending (booking is updated in place)
 * - Completed / Ineligible / Absent: only from Upcoming / Pending / Eligible (booking moves to donation)
 */
function validateStatusChange(current, target) {
  const from = normaliseStatus(current);
  if (target === STATUS.ELIGIBLE) {
    return [STATUS.UPCOMING, STATUS.PENDING].includes(from)
      ? { ok: true, close: false }
      : { ok: false, code: 'BAD_TRANSITION', message: `Cannot mark a ${from} booking as Eligible` };
  }
  if (CLOSING_STATUSES.includes(target)) {
    return ACTIVE_STATUSES.includes(from)
      ? { ok: true, close: true }
      : { ok: false, code: 'BAD_TRANSITION', message: `Cannot close a ${from} booking` };
  }
  return {
    ok: false,
    code: 'BAD_STATUS',
    message: `status must be one of ${[STATUS.ELIGIBLE, ...CLOSING_STATUSES].join(', ')}`,
  };
}

module.exports = { parseBookingDate, normaliseStatus, isActiveStatus, validateNewBooking, countSlot, validateStatusChange };
