const { MY_OFFSET, MY_OFFSET_MS } = require('../config');

const DAY_MS = 24 * 60 * 60 * 1000;

/** Start (00:00 Malaysia time) of the Malaysia calendar day containing `date`, plus offsetDays. */
function myDayStart(date, offsetDays = 0) {
  const shifted = new Date(new Date(date).getTime() + MY_OFFSET_MS);
  const startUtcMs = Date.UTC(shifted.getUTCFullYear(), shifted.getUTCMonth(), shifted.getUTCDate()) - MY_OFFSET_MS;
  return new Date(startUtcMs + offsetDays * DAY_MS);
}

/** "YYYY-MM-DD" of `date` in Malaysia time (toISOString() would give the UTC date). */
function toMyDateString(date) {
  if (!date) return null;
  return new Date(new Date(date).getTime() + MY_OFFSET_MS).toISOString().slice(0, 10);
}

/** Build a Date at 00:00 MYT from y/m/d, or null if the day does not exist (e.g. 31/02). */
function myDateFromParts(y, m, d) {
  const date = new Date(`${String(y).padStart(4, '0')}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}T00:00:00${MY_OFFSET}`);
  if (Number.isNaN(date.getTime())) return null;
  return toMyDateString(date) === `${String(y).padStart(4, '0')}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`
    ? date
    : null;
}

module.exports = { DAY_MS, myDayStart, toMyDateString, myDateFromParts };
