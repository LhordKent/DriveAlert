const { sendEmailVerification, applyActionCode, reload } = require('firebase/auth');
const PROJECT = 'drivealert-lhordkent';
const ADDRESS = process.env.FIREBASE_AUTH_EMULATOR_HOST || '127.0.0.1:9099';

async function verifyEmail(auth, user) {
  await sendEmailVerification(user);
  const response = await fetch(`http://${ADDRESS}/emulator/v1/projects/${PROJECT}/oobCodes`);
  if (!response.ok) throw new Error('Unable to read emulator verification codes');
  const { oobCodes } = await response.json();
  const code = oobCodes.filter(item => item.email === user.email && item.requestType === 'VERIFY_EMAIL').at(-1);
  if (!code) throw new Error('Verification email was not sent');
  await applyActionCode(auth, code.oobCode);
  await reload(user);
  await user.getIdToken(true);
}
module.exports = { verifyEmail };
