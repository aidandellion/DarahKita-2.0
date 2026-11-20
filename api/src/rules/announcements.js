const { myDateFromParts, myDayStart } = require('./dates');

/**
 * Parse event.eventDate. The app writes "dd/MM/yyyy" (StaffDashboardActivity date picker);
 * "yyyy-MM-dd" is also accepted. Returns 00:00 MYT of that day, or null if not recognised.
 */
function parseEventDate(eventDate) {
  if (typeof eventDate !== 'string') return null;
  const s = eventDate.trim();
  let m = /^(\d{1,2})\/(\d{1,2})\/(\d{4})$/.exec(s);
  if (m) return myDateFromParts(+m[3], +m[2], +m[1]);
  m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(s);
  if (m) return myDateFromParts(+m[1], +m[2], +m[3]);
  return null;
}

/**
 * An announcement expires the day AFTER its eventDate (Malaysia time).
 * Anything unparseable is treated as not expired (never delete by guess).
 */
function isExpired(eventDate, now = new Date()) {
  const day = parseEventDate(eventDate);
  if (!day) return false;
  return now >= myDayStart(day, 1);
}

module.exports = { parseEventDate, isExpired };
