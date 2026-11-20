// Data-access layer: the ONLY file that talks to Firestore.
// Routes call these functions, tests replace them with fakes.
// Every query uses a single field only (equality or a range), so no composite index is needed;
// the rest of the filtering happens in code.
const { init } = require('./firebase');
const { COLLECTIONS: C, STATUS, BOOKING_STORE_HOUR_MY } = require('./config');
const { isActiveStatus, countSlot } = require('./rules/booking');
const { myDayStart } = require('./rules/dates');
const { stockDocId } = require('./rules/stock');

function createRepo() {
  const admin = init();
  const db = admin.firestore();
  const { Timestamp, FieldValue, GeoPoint } = admin.firestore;

  const toDate = (v) => (v && typeof v.toDate === 'function' ? v.toDate() : v ? new Date(v) : null);
  const snap = (d) => (d.exists ? { id: d.id, ...d.data() } : null);
  const withDate = (d) => ({ id: d.id, ...d.data(), bookingDate: toDate(d.data().bookingDate) });

  async function listBookingsBetween(start, end) {
    const q = await db
      .collection(C.booking)
      .where('bookingDate', '>=', Timestamp.fromDate(start))
      .where('bookingDate', '<', Timestamp.fromDate(end))
      .get();
    return q.docs.map(withDate);
  }

  return {
    async getUser(uid) {
      return snap(await db.collection(C.users).doc(uid).get());
    },

    async getCentre(centreID) {
      return snap(await db.collection(C.centre).doc(centreID).get());
    },

    /** bookingDates of the donor's Completed donations (max is taken by the caller). */
    async listCompletedDonationDates(uid) {
      const q = await db.collection(C.donation).where('userID', '==', uid).get();
      return q.docs
        .filter((d) => d.data().status === STATUS.COMPLETED)
        .map((d) => toDate(d.data().bookingDate))
        .filter(Boolean);
    },

    async getActiveBookingsForUser(uid) {
      const q = await db.collection(C.booking).where('userID', '==', uid).get();
      return q.docs.map(withDate).filter((b) => isActiveStatus(b.status));
    },

    /** Active bookings for one centre/timeslot on the Malaysia day starting at dayStart. */
    async countSlotBookings(centreID, dayStart, timeslot) {
      const day = await listBookingsBetween(dayStart, myDayStart(dayStart, 1));
      return countSlot(day, centreID, timeslot);
    },

    /** dayStart is 00:00 MYT; stored at BOOKING_STORE_HOUR_MY so the app's "today" queries find it. */
    async createBooking({ userID, centreID, bookingDate, timeslot }) {
      const stored = new Date(bookingDate.getTime() + BOOKING_STORE_HOUR_MY * 3600 * 1000);
      const ref = db.collection(C.booking).doc();
      await ref.set({
        userID,
        centreID,
        bookingDate: Timestamp.fromDate(stored),
        timeslot,
        status: STATUS.UPCOMING,
        checkedIn: false,
        checkinLocation: null,
        reminderSent: false,
      });
      return ref.id;
    },

    async getBooking(id) {
      const d = await db.collection(C.booking).doc(id).get();
      return d.exists ? withDate(d) : null;
    },

    /** Same as the app: only checkedIn + checkinLocation change, status stays as it is. */
    async markCheckedIn(id, lat, lng) {
      await db.collection(C.booking).doc(id).update({
        checkedIn: true,
        checkinLocation: new GeoPoint(lat, lng),
      });
    },

    /** Eligible: stays in the live queue, only the status changes (QueueAdapter.markEligible). */
    async setBookingStatus(id, status) {
      await db.collection(C.booking).doc(id).update({ status });
    },

    /**
     * Completed / Ineligible / Absent, like QueueAdapter.finalizeStatus: copy every booking field to
     * `donation` with the final status, bookingID, recordedAt and the donor's bloodType ("N/A" if unknown),
     * then delete the booking. Done in a transaction so a booking can't be closed twice.
     */
    async closeBooking(bookingId, status) {
      const bookingRef = db.collection(C.booking).doc(bookingId);
      const donationRef = db.collection(C.donation).doc();
      await db.runTransaction(async (tx) => {
        const b = await tx.get(bookingRef);
        if (!b.exists) {
          const e = new Error('Booking not found');
          e.code = 'BOOKING_NOT_FOUND';
          throw e;
        }
        const data = b.data();
        const user = data.userID ? await tx.get(db.collection(C.users).doc(data.userID)) : null;
        const bloodType = (user && user.exists && user.data().bloodType) || 'N/A';
        tx.set(donationRef, {
          ...data,
          status,
          bookingID: b.id,
          recordedAt: Timestamp.now(),
          bloodType,
        });
        tx.delete(bookingRef);
      });
      return donationRef.id;
    },

    /** Same doc id and fields as StockActivity.saveToFirestore. */
    async setStock(centreID, bloodType, outStock) {
      await db
        .collection(C.bloodStock)
        .doc(stockDocId(centreID, bloodType))
        .set({ centreID, bloodType, outStock, lastUpdated: FieldValue.serverTimestamp() }, { merge: true });
    },

    listBookingsBetween,

    async markReminderSent(id) {
      await db.collection(C.booking).doc(id).update({ reminderSent: true });
    },

    async listEvents() {
      const q = await db.collection(C.event).get();
      return q.docs.map((d) => ({ id: d.id, ...d.data() }));
    },

    async deleteEvent(id) {
      await db.collection(C.event).doc(id).delete();
    },

    async sendTopicPush(topic, title, body) {
      await admin.messaging().send({ topic, notification: { title, body } });
    },
  };
}

module.exports = { createRepo };
