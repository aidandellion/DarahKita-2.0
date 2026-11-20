const { createApp } = require('./app');
const { createRepo } = require('./repo');
const { init } = require('./firebase');
const { sendReminderEmail } = require('./email');

const admin = init();
const app = createApp({
  repo: createRepo(),
  verifyIdToken: (token) => admin.auth().verifyIdToken(token),
  sendReminderEmail,
});

const port = process.env.PORT || 3000;
app.listen(port, () => console.log(`DarahKita API listening on ${port}`));
