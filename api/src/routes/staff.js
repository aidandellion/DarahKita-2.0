const express = require('express');
const { nextEligibleFromDonations } = require('../rules/eligibility');
const { validateStatusChange } = require('../rules/booking');
const { isBloodType } = require('../rules/stock');
const { toMyDateString } = require('../rules/dates');
const { STATUS } = require('../config');

const err = (res, status, code, message) => res.status(status).json({ error: code, message });

module.exports = function staffRoutes({ repo }) {
  const r = express.Router();

  // PATCH /v1/staff/bookings/:id/status  { status: "Eligible" | "Completed" | "Ineligible" | "Absent" }
  r.patch('/bookings/:id/status', async (req, res, next) => {
    try {
      const { status } = req.body || {};
      const booking = await repo.getBooking(req.params.id);
      if (!booking) return err(res, 404, 'BOOKING_NOT_FOUND', 'Booking not found');
      // centre scoping: staff can only touch their own centre (req.user.centreID is already trimmed)
      if (!req.user.centreID || String(booking.centreID || '').trim() !== req.user.centreID) {
        return err(res, 403, 'FORBIDDEN', 'Different centre');
      }

      const change = validateStatusChange(booking.status, status);
      if (!change.ok) return err(res, change.code === 'BAD_STATUS' ? 400 : 409, change.code, change.message);

      let donationId = null;
      if (change.close) {
        donationId = await repo.closeBooking(booking.id, status);
      } else {
        await repo.setBookingStatus(booking.id, status);
      }

      // Next eligible date = latest Completed donation + WAIT_DAYS, including this one if just completed.
      const completed = booking.userID ? await repo.listCompletedDonationDates(booking.userID) : [];
      if (status === STATUS.COMPLETED) completed.push(booking.bookingDate);
      const nextEligible = nextEligibleFromDonations(completed);

      res.json({ bookingId: booking.id, donationId, status, nextEligibleDate: toMyDateString(nextEligible) });
    } catch (e) {
      if (e.code === 'BOOKING_NOT_FOUND') return err(res, 404, 'BOOKING_NOT_FOUND', 'Booking not found');
      next(e);
    }
  });

  // PUT /v1/staff/stock  { bloodType: "O-", insufficient: true }
  r.put('/stock', async (req, res, next) => {
    try {
      const { bloodType, insufficient } = req.body || {};
      if (!isBloodType(bloodType) || typeof insufficient !== 'boolean') {
        return err(res, 400, 'BAD_REQUEST', 'bloodType (e.g. "O-") and insufficient (boolean) are required');
      }
      const centreID = req.user.centreID;
      if (!centreID) return err(res, 403, 'NO_CENTRE', 'Staff profile has no centreID');
      await repo.setStock(centreID, bloodType, insufficient);

      let pushed = false;
      if (insufficient) {
        try {
          await repo.sendTopicPush(
            `centre_${centreID}`,
            'Urgent blood needed',
            `${bloodType} stock is low at your centre. Book a slot to donate.`
          );
          pushed = true;
        } catch {
          // push is a bonus; stock update already succeeded
        }
      }
      res.json({ bloodType, insufficient, pushed });
    } catch (e) {
      next(e);
    }
  });

  return r;
};
