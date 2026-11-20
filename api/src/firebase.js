const admin = require('firebase-admin');

let initialised = false;

function init() {
  if (initialised) return admin;
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT;
  if (!raw) throw new Error('FIREBASE_SERVICE_ACCOUNT env var is missing');
  admin.initializeApp({ credential: admin.credential.cert(JSON.parse(raw)) });
  initialised = true;
  return admin;
}

module.exports = { init };
