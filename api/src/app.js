const express = require('express');
const cors = require('cors');
const { authenticate, requireRole, requireJobSecret } = require('./auth');
const donorRoutes = require('./routes/donor');
const staffRoutes = require('./routes/staff');
const jobRoutes = require('./routes/jobs');

/**
 * Dependencies are injected so tests can pass fakes (no Firebase needed).
 * @param {{ repo: object, verifyIdToken: (t:string)=>Promise<{uid:string}>, sendReminderEmail: Function, now?: Function }} deps
 */
function createApp({ repo, verifyIdToken, sendReminderEmail, now }) {
  const app = express();
  app.use(cors());
  app.use(express.json());

  // Public: also what the scheduler pings to wake the free host
  app.get('/health', (req, res) => res.json({ status: 'ok', time: new Date().toISOString() }));

  const auth = authenticate({ verifyIdToken, repo });
  app.use('/v1/jobs', requireJobSecret, jobRoutes({ repo, sendReminderEmail, now }));
  app.use('/v1/staff', auth, requireRole('staff'), staffRoutes({ repo }));
  app.use('/v1', auth, requireRole('donor'), donorRoutes({ repo, now }));

  app.use((req, res) => res.status(404).json({ error: 'NOT_FOUND', message: 'No such endpoint' }));
  // eslint-disable-next-line no-unused-vars
  app.use((e, req, res, next) => {
    console.error(e);
    res.status(500).json({ error: 'INTERNAL', message: 'Something went wrong' });
  });
  return app;
}

module.exports = { createApp };
