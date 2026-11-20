// Server-side EmailJS call (REST). Needs the private key, so it must never run inside the app.
// In EmailJS: Account > Security > allow API calls for non-browser applications.
// Only the reminder email moves to the API; the booking confirmation is still sent by the app.

/**
 * Template params use the same names as the app's EmailHelper.sendReminder,
 * so the existing EmailJS reminder template works unchanged.
 * @param {{ email: string, name: string, centre: string, date: string, time: string }} p
 *   date as "yyyy-MM-dd", time like "09:00 AM"
 */
async function sendReminderEmail({ email, name, centre, date, time }) {
  const {
    EMAILJS_REMINDER_SERVICE_ID,
    EMAILJS_REMINDER_TEMPLATE_ID,
    EMAILJS_PUBLIC_KEY,
    EMAILJS_PRIVATE_KEY,
  } = process.env;
  if (!EMAILJS_REMINDER_SERVICE_ID || !EMAILJS_REMINDER_TEMPLATE_ID || !EMAILJS_PUBLIC_KEY || !EMAILJS_PRIVATE_KEY) {
    throw new Error('EmailJS env vars are not set');
  }
  const res = await fetch('https://api.emailjs.com/api/v1.0/email/send', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      service_id: EMAILJS_REMINDER_SERVICE_ID,
      template_id: EMAILJS_REMINDER_TEMPLATE_ID,
      user_id: EMAILJS_PUBLIC_KEY,
      accessToken: EMAILJS_PRIVATE_KEY,
      template_params: { email, name, centre, date, time },
    }),
  });
  if (!res.ok) throw new Error(`EmailJS ${res.status}: ${await res.text()}`);
}

module.exports = { sendReminderEmail };
