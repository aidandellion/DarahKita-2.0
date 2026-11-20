const express = require('express');
const { isExpired } = require('../rules/announcements');
const { isActiveStatus } = require('../rules/booking');
const { myDayStart, toMyDateString } = require('../rules/dates');

module.exports = function jobRoutes({ repo, sendReminderEmail, now = () => new Date() }) {
  const r = express.Router();

  // POST /v1/jobs/send-reminders  -> emails everyone with an active booking TOMORROW (Malaysia time), once each
  r.post('/send-reminders', async (req, res, next) => {
    try {
      const t = now();
      const bookings = await repo.listBookingsBetween(myDayStart(t, 1), myDayStart(t, 2));
      let sent = 0;
      let failed = 0;
      for (const b of bookings) {
        if (!isActiveStatus(b.status) || b.reminderSent) continue;
        try {
          const [user, centre] = await Promise.all([repo.getUser(b.userID), repo.getCentre(String(b.centreID || '').trim())]);
          if (!user?.email) continue;
          // Same template params as the app's EmailHelper.sendReminder
          await sendReminderEmail({
            email: user.email,
            name: user.fullName || user.name || 'Donor',
            centre: centre?.name || 'the donation centre',
            date: toMyDateString(b.bookingDate),
            time: b.timeslot,
          });
          await repo.markReminderSent(b.id);
          sent++;
        } catch {
          failed++;
        }
      }
      res.json({ checked: bookings.length, sent, failed });
    } catch (e) {
      next(e);
    }
  });

  // POST /v1/jobs/expire-announcements  -> deletes events whose eventDate has passed
  r.post('/expire-announcements', async (req, res, next) => {
    try {
      const t = now();
      const events = await repo.listEvents();
      const expired = events.filter((e) => isExpired(e.eventDate, t));
      for (const e of expired) await repo.deleteEvent(e.id);
      res.json({ checked: events.length, deleted: expired.length });
    } catch (e) {
      next(e);
    }
  });

  return r;
};
