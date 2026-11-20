const { WAIT_DAYS } = require('../config');
const { DAY_MS, myDayStart } = require('./dates');

// Same questions, ids and disqualifying answers as the app's EligibilityScreeningActivity.buildQuestions().
// ok: the answer that keeps the donor eligible (true = Yes, false = No)
const QUESTIONS = [
  { id: 'goodHealth', text: 'Are you currently in good health?', ok: true },
  { id: 'ageValid', text: 'Are you aged between 18 and 60 years old?', ok: true },
  { id: 'weightValid', text: 'Do you weigh at least 45 kg?', ok: true },
  { id: 'recentDonation', text: 'Have you donated blood in the last 56 days?', ok: false },
  { id: 'onMedication', text: 'Are you currently taking any prescribed medication or antibiotics?', ok: false },
  { id: 'recentVaccination', text: 'Have you received any vaccination in the past 4 weeks?', ok: false },
  {
    id: 'recentTattooOrSurgery',
    text: 'Have you had a tattoo, piercing, or any surgery/dental procedure in the last 6 months?',
    ok: false,
  },
  {
    id: 'pregnantOrBreastfeeding',
    text: 'Are you currently pregnant, breastfeeding, or given birth in the last 6 months?',
    ok: false,
  },
  {
    id: 'chronicIllness',
    text: 'Do you have any chronic illness (e.g. diabetes, heart disease, hepatitis, HIV)?',
    ok: false,
  },
];

function addDays(date, days) {
  return new Date(date.getTime() + days * DAY_MS);
}

/**
 * Next allowed donation day: the Malaysia day of the last donation + waitDays, at 00:00 MYT.
 * Day-based like the app (BookingHubActivity zeroes the time), so the time of the booking doesn't matter.
 */
function nextEligibleDate(lastDonation, waitDays = WAIT_DAYS) {
  return myDayStart(lastDonation, waitDays);
}

/**
 * Next eligible date from the bookingDates of the donor's Completed donations.
 * Returns null when there are none (never donated = eligible now).
 * @param {Array<Date|null|undefined>} completedDates
 */
function nextEligibleFromDonations(completedDates, waitDays = WAIT_DAYS) {
  const times = (completedDates || [])
    .map((d) => (d ? new Date(d).getTime() : NaN))
    .filter((t) => !Number.isNaN(t));
  if (times.length === 0) return null;
  return nextEligibleDate(new Date(Math.max(...times)), waitDays);
}

/**
 * Returns every failing reason (the app stops at the first one, the API reports all).
 * @param {object} p
 * @param {Object<string, boolean>} p.answers questionnaire answers by question id
 * @param {Date|null} p.nextEligible next eligible date (null = never donated)
 * @param {Date} [p.now]
 * @returns {{eligible: boolean, reasons: string[]}}
 */
function checkEligibility({ answers, nextEligible, now = new Date() }) {
  const reasons = [];

  if (nextEligible && now < nextEligible) {
    const daysLeft = Math.ceil((nextEligible - now) / DAY_MS);
    reasons.push(`Too soon since last donation (${daysLeft} day(s) left)`);
  }

  for (const q of QUESTIONS) {
    if (typeof answers?.[q.id] !== 'boolean') {
      reasons.push(`Missing answer: ${q.id}`);
    } else if (answers[q.id] !== q.ok) {
      reasons.push(`Not eligible: ${q.text}`);
    }
  }

  return { eligible: reasons.length === 0, reasons };
}

module.exports = { QUESTIONS, addDays, nextEligibleDate, nextEligibleFromDonations, checkEligibility, DAY_MS };
