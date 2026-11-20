// One place for tunable rules and Firestore collection names.
// Values below were checked against the Android app (see README "Aligned with the app").

const num = (v, d) => (v !== undefined && v !== '' && !Number.isNaN(Number(v)) ? Number(v) : d);

module.exports = {
  WAIT_DAYS: num(process.env.WAIT_DAYS, 56),
  CHECKIN_RADIUS_M: num(process.env.CHECKIN_RADIUS_M, 100), // the app currently uses 200 m
  SLOT_CAPACITY: num(process.env.SLOT_CAPACITY, 5), // new rule, the app has no slot limit
  MY_OFFSET: '+08:00', // Malaysia time, no daylight saving
  MY_OFFSET_MS: 8 * 60 * 60 * 1000,

  // Hour (Malaysia time) stored in booking.bookingDate for API-created bookings.
  // Noon MYT is 04:00 UTC on the same date, so the app's "today" queries (local 00:00-23:59:59)
  // find it whether the phone/emulator runs on Malaysia time or UTC.
  BOOKING_STORE_HOUR_MY: 12,

  COLLECTIONS: {
    users: 'users',
    booking: 'booking', // live bookings
    donation: 'donation', // closed records (Completed / Ineligible / Absent)
    centre: 'centre',
    bloodStock: 'bloodStock',
    event: 'event',
  },

  // Status strings the app writes.
  // Upcoming = donor booked (Booking model), Pending = shown when status is missing/seeded,
  // Eligible = staff passed the donor at the centre (QueueAdapter.markEligible).
  STATUS: {
    UPCOMING: 'Upcoming',
    PENDING: 'Pending',
    ELIGIBLE: 'Eligible',
    COMPLETED: 'Completed',
    INELIGIBLE: 'Ineligible',
    ABSENT: 'Absent',
  },
  ACTIVE_STATUSES: ['Upcoming', 'Pending', 'Eligible'],
  CLOSING_STATUSES: ['Completed', 'Ineligible', 'Absent'],

  BLOOD_TYPES: ['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'],
};
