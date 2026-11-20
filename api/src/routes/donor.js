const express = require('express');
const { checkEligibility, nextEligibleFromDonations } = require('../rules/eligibility');
const { parseBookingDate, validateNewBooking, isActiveStatus } = require('../rules/booking');
const { verifyCheckIn } = require('../rules/checkin');
const { toMyDateString } = require('../rules/dates');
const { STATUS } = require('../config');

const err = (res, status, code, message, extra = {}) => res.status(status).json({ error: code, message, ...extra });

module.exports = function donorRoutes({ repo, now = () => new Date() }) {
  const r = express.Router();

  // Latest Completed donation.bookingDate + WAIT_DAYS (null = never donated). Not stored on users.
  const nextEligibleFor = async (uid) => nextEligibleFromDonations(await repo.listCompletedDonationDates(uid));

  // GET /v1/me/eligibility  -> when can I donate next?
  r.get('/me/eligibility', async (req, res, next) => {
    try {
      const nextEligible = await nextEligibleFor(req.user.uid);
      res.json({
        nextEligibleDate: toMyDateString(nextEligible),
        eligibleNow: !nextEligible || now() >= nextEligible,
      });
    } catch (e) {
      next(e);
    }
  });

  // POST /v1/eligibility/check  { answers: { goodHealth: true, ... } }
  r.post('/eligibility/check', async (req, res, next) => {
    try {
      const nextEligible = await nextEligibleFor(req.user.uid);
      const result = checkEligibility({ answers: req.body?.answers, nextEligible, now: now() });
      res.json({ ...result, nextEligibleDate: toMyDateString(nextEligible) });
    } catch (e) {
      next(e);
    }
  });

  // POST /v1/bookings  { centreId, bookingDate: "2026-12-01", timeslot: "10:00 AM", answers }
  r.post('/bookings', async (req, res, next) => {
    try {
      const { centreId, bookingDate: dateStr, timeslot, answers } = req.body || {};
      const bookingDate = parseBookingDate(dateStr);
      if (!centreId || !bookingDate || !timeslot) {
        return err(res, 400, 'BAD_REQUEST', 'centreId, bookingDate (YYYY-MM-DD) and timeslot are required');
      }

      const t = now();
      const nextEligible = await nextEligibleFor(req.user.uid);
      const elig = checkEligibility({ answers, nextEligible, now: t });
      if (!elig.eligible) return err(res, 422, 'NOT_ELIGIBLE', 'Eligibility check failed', { reasons: elig.reasons });

      const centre = await repo.getCentre(centreId);
      if (!centre) return err(res, 404, 'CENTRE_NOT_FOUND', 'Centre does not exist');

      const [active, slotCount] = await Promise.all([
        repo.getActiveBookingsForUser(req.user.uid),
        repo.countSlotBookings(centreId, bookingDate, timeslot),
      ]);

      const check = validateNewBooking({
        bookingDate,
        timeslot,
        nextEligible,
        activeBookingCount: active.length,
        slotCount,
        now: t,
      });
      if (!check.ok) return err(res, 409, check.code, check.message);

      const bookingId = await repo.createBooking({
        userID: req.user.uid,
        centreID: centreId,
        bookingDate,
        timeslot,
      });
      res.status(201).json({ bookingId, status: STATUS.UPCOMING });
    } catch (e) {
      next(e);
    }
  });

  // POST /v1/bookings/:id/check-in  { lat, lng }
  r.post('/bookings/:id/check-in', async (req, res, next) => {
    try {
      const booking = await repo.getBooking(req.params.id);
      if (!booking || booking.userID !== req.user.uid) {
        return err(res, 404, 'BOOKING_NOT_FOUND', 'Booking not found');
      }
      if (!isActiveStatus(booking.status)) return err(res, 409, 'BAD_STATUS', `Booking is ${booking.status}`);
      if (booking.checkedIn) return err(res, 409, 'ALREADY_CHECKED_IN', 'Booking is already checked in');

      const centre = await repo.getCentre(booking.centreID);
      const result = verifyCheckIn({
        userLat: req.body?.lat,
        userLng: req.body?.lng,
        centreLat: centre?.latitude,
        centreLng: centre?.longitude,
      });
      if (!result.ok) {
        const status = result.code === 'BAD_COORDS' ? 400 : 403;
        return err(res, status, result.code, 'Check-in failed', { distanceMetres: result.distance });
      }
      // Status is NOT changed (same as the app): only checkedIn + checkinLocation.
      await repo.markCheckedIn(booking.id, req.body.lat, req.body.lng);
      res.json({ checkedIn: true, status: booking.status, distanceMetres: result.distance });
    } catch (e) {
      next(e);
    }
  });

  return r;
};
