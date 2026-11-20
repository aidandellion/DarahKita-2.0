const crypto = require('crypto');

/**
 * Checks the Firebase ID token sent by the app (Authorization: Bearer <token>),
 * then loads the user's Firestore record to get role and centreID.
 */
function authenticate({ verifyIdToken, repo }) {
  return async (req, res, next) => {
    const header = req.get('authorization') || '';
    const token = header.startsWith('Bearer ') ? header.slice(7) : null;
    if (!token) return res.status(401).json({ error: 'UNAUTHENTICATED', message: 'Missing bearer token' });
    try {
      const decoded = await verifyIdToken(token);
      const user = await repo.getUser(decoded.uid);
      if (!user) return res.status(403).json({ error: 'NO_PROFILE', message: 'No user profile found' });
      // Some docs have stray spaces (StockActivity trims centreID too), so trim both here once.
      const trim = (v) => (typeof v === 'string' ? v.trim() : v ?? null);
      req.user = { uid: decoded.uid, ...user, role: trim(user.role), centreID: trim(user.centreID) };
      next();
    } catch {
      res.status(401).json({ error: 'UNAUTHENTICATED', message: 'Invalid or expired token' });
    }
  };
}

const requireRole = (role) => (req, res, next) =>
  req.user?.role === role
    ? next()
    : res.status(403).json({ error: 'FORBIDDEN', message: `${role} role required` });

/** Scheduled jobs are called by GitHub Actions, not by app users. */
function requireJobSecret(req, res, next) {
  const expected = process.env.JOB_SECRET || '';
  const got = req.get('x-job-secret') || '';
  const a = Buffer.from(expected);
  const b = Buffer.from(got);
  if (!expected || a.length !== b.length || !crypto.timingSafeEqual(a, b)) {
    return res.status(401).json({ error: 'UNAUTHENTICATED', message: 'Bad job secret' });
  }
  next();
}

module.exports = { authenticate, requireRole, requireJobSecret };
