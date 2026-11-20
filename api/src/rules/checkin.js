const { CHECKIN_RADIUS_M } = require('../config');

const toRad = (d) => (d * Math.PI) / 180;

/** Great-circle distance in metres between two lat/lng points. */
function distanceMetres(lat1, lng1, lat2, lng2) {
  const R = 6371000;
  const dLat = toRad(lat2 - lat1);
  const dLng = toRad(lng2 - lng1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(a));
}

function verifyCheckIn({ userLat, userLng, centreLat, centreLng, radius = CHECKIN_RADIUS_M }) {
  const nums = [userLat, userLng, centreLat, centreLng];
  if (nums.some((n) => typeof n !== 'number' || Number.isNaN(n))) {
    return { ok: false, code: 'BAD_COORDS', distance: null };
  }
  const distance = Math.round(distanceMetres(userLat, userLng, centreLat, centreLng));
  return distance <= radius
    ? { ok: true, distance }
    : { ok: false, code: 'NOT_AT_CENTRE', distance };
}

module.exports = { distanceMetres, verifyCheckIn };
