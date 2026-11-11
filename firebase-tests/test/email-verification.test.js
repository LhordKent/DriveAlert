const assert = require('node:assert/strict');
const { initializeApp, deleteApp } = require('firebase/app');
const { getAuth, connectAuthEmulator, createUserWithEmailAndPassword } = require('firebase/auth');
const { getFirestore, connectFirestoreEmulator, doc, setDoc, getDoc, serverTimestamp } = require('firebase/firestore');
const { verifyEmail } = require('./verify-email');
const PROJECT = 'drivealert-lhordkent';
const AUTH = process.env.FIREBASE_AUTH_EMULATOR_HOST || '127.0.0.1:9099';
const FIRESTORE = process.env.FIRESTORE_EMULATOR_HOST || '127.0.0.1:8080';

describe('email verification access gate', () => {
  let app;
  afterEach(async () => { if (app) await deleteApp(app); });
  it('denies direct data access until the emailed link is completed and the token refreshed', async () => {
    app = initializeApp({ projectId: PROJECT, apiKey: 'demo-key' }, 'verification-' + Date.now());
    const auth = getAuth(app);
    connectAuthEmulator(auth, `http://${AUTH}`, { disableWarnings: true });
    const db = getFirestore(app);
    connectFirestoreEmulator(db, FIRESTORE.split(':')[0], Number(FIRESTORE.split(':')[1]));
    const { user } = await createUserWithEmailAndPassword(auth, `unverified-${Date.now()}@gmail.com`, 'Password123!');
    const profile = doc(db, 'users', user.uid);
    const data = { uid: user.uid, email: user.email, firstName: 'Pending', lastName: 'User', accountStatus: 'ACTIVE', registeredAt: serverTimestamp() };
    const denied = error => error.code === 'permission-denied';
    await assert.rejects(setDoc(profile, data), denied);
    await assert.rejects(getDoc(profile), denied);
    await assert.rejects(getDoc(doc(db, 'connectionCodes', 'DA23456789ABCDEFGHJKLMNPQRST')), denied);
    await assert.rejects(getDoc(doc(db, 'devices', 'AABBCCDDEEFF')), denied);
    assert.equal(user.emailVerified, false);
    await verifyEmail(auth, user);
    assert.equal(user.emailVerified, true);
    await setDoc(profile, data);
    assert.equal((await getDoc(profile)).data().uid, user.uid);
  });
});
