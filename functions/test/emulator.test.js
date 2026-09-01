const assert = require("node:assert/strict");
const {
  initializeApp,
  deleteApp,
} = require("firebase/app");
const {
  connectAuthEmulator,
  createUserWithEmailAndPassword,
  getAuth,
} = require("firebase/auth");
const {
  Timestamp,
  connectFirestoreEmulator,
  doc,
  getDoc,
  getDocs,
  getFirestore,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  collection,
  where,
} = require("firebase/firestore");
const {
  connectFunctionsEmulator,
  getFunctions,
  httpsCallable,
} = require("firebase/functions");

const PROJECT_ID = "drivealert-lhordkent";
const GENERIC_MESSAGE = "If this email belongs to a DriveAlert account, the request will appear in the app.";
let appCounter = 0;

async function clearEmulators() {
  await fetch(`http://127.0.0.1:8080/emulator/v1/projects/${PROJECT_ID}/databases/(default)/documents`, {
    method: "DELETE",
  });
  await fetch(`http://127.0.0.1:9099/emulator/v1/projects/${PROJECT_ID}/accounts`, {
    method: "DELETE",
  });
}

async function createClient(email, role) {
  const app = initializeApp(
    { projectId: PROJECT_ID, apiKey: "demo-key", appId: `demo-${++appCounter}` },
    `test-${appCounter}`,
  );
  const auth = getAuth(app);
  connectAuthEmulator(auth, "http://127.0.0.1:9099", { disableWarnings: true });
  const firestore = getFirestore(app);
  connectFirestoreEmulator(firestore, "127.0.0.1", 8080);
  const functions = getFunctions(app, "asia-southeast1");
  connectFunctionsEmulator(functions, "127.0.0.1", 5001);
  const credential = await createUserWithEmailAndPassword(auth, email, "Password123!");
  await setDoc(doc(firestore, "users", credential.user.uid), {
    firstName: email.split("@")[0],
    middleName: null,
    lastName: "Tester",
    email,
    phoneNumber: null,
    userRole: role,
    accountStatus: "ACTIVE",
    registeredAt: serverTimestamp(),
    deactivatedAt: null,
  });
  return { app, auth, firestore, functions, uid: credential.user.uid, email };
}

async function sendRequest(client, email, inviterRole) {
  const result = await httpsCallable(client.functions, "sendTrustedContactRequest")({ email, inviterRole });
  assert.equal(result.data.message, GENERIC_MESSAGE);
}

describe("DriveAlert Firebase emulators", () => {
  const apps = [];

  beforeEach(async () => {
    await clearEmulators();
  });

  afterEach(async () => {
    await Promise.all(apps.splice(0).map(deleteApp));
  });

  it("supports a Driver-initiated request and only lets the recipient approve", async () => {
    const driver = await createClient("driver@example.com", "DRIVER");
    const trusted = await createClient("trusted@example.com", "TRUSTED_CONTACT");
    apps.push(driver.app, trusted.app);

    await sendRequest(driver, trusted.email, "DRIVER");
    const id = `${driver.uid}__${trusted.uid}`;
    const driverRef = doc(driver.firestore, "trustedContactConnections", id);
    const trustedRef = doc(trusted.firestore, "trustedContactConnections", id);
    assert.equal((await getDoc(driverRef)).data().requestedByUserId, driver.uid);

    await assert.rejects(updateDoc(driverRef, { status: "APPROVED", approvedAt: serverTimestamp() }));
    await updateDoc(trustedRef, { status: "APPROVED", approvedAt: serverTimestamp() });
    assert.equal((await getDoc(driverRef)).data().status, "APPROVED");
  });

  it("supports a Trusted Contact-initiated request and only lets the Driver approve", async () => {
    const driver = await createClient("driver2@example.com", "DRIVER");
    const trusted = await createClient("trusted2@example.com", "TRUSTED_CONTACT");
    apps.push(driver.app, trusted.app);

    await sendRequest(trusted, driver.email, "TRUSTED_CONTACT");
    const id = `${driver.uid}__${trusted.uid}`;
    const driverRef = doc(driver.firestore, "trustedContactConnections", id);
    const trustedRef = doc(trusted.firestore, "trustedContactConnections", id);
    assert.equal((await getDoc(trustedRef)).data().requestedByUserId, trusted.uid);

    await assert.rejects(updateDoc(trustedRef, { status: "APPROVED", approvedAt: serverTimestamp() }));
    await updateDoc(driverRef, { status: "APPROVED", approvedAt: serverTimestamp() });
    assert.equal((await getDoc(trustedRef)).data().status, "APPROVED");
  });

  it("returns the same generic result and creates nothing for an unknown email", async () => {
    const driver = await createClient("known@example.com", "DRIVER");
    apps.push(driver.app);
    await sendRequest(driver, "missing@example.com", "DRIVER");
    const connections = await getDocs(query(
      collection(driver.firestore, "trustedContactConnections"),
      where("driverUserId", "==", driver.uid),
    ));
    assert.equal(connections.empty, true);
  });

  it("enforces future-only Stage 3 access while the relationship is approved", async () => {
    const driver = await createClient("timeline-driver@example.com", "DRIVER");
    const trusted = await createClient("timeline-trusted@example.com", "TRUSTED_CONTACT");
    apps.push(driver.app, trusted.app);
    await sendRequest(driver, trusted.email, "DRIVER");
    const connectionId = `${driver.uid}__${trusted.uid}`;
    await updateDoc(doc(trusted.firestore, "trustedContactConnections", connectionId), {
      status: "APPROVED",
      approvedAt: serverTimestamp(),
    });
    const connection = (await getDoc(doc(driver.firestore, "trustedContactConnections", connectionId))).data();
    const approvedAt = connection.approvedAt;

    const oldRecord = doc(driver.firestore, "users", driver.uid, "stageSyncRecords", "old");
    const futureRecord = doc(driver.firestore, "users", driver.uid, "stageSyncRecords", "future");
    const common = {
      driverUserId: driver.uid,
      warningStage: "STAGE_3",
      syncStatus: "SYNCED",
      signs: ["YAWNING"],
      occurredAt: Timestamp.fromMillis(approvedAt.toMillis() + 2_000),
    };
    await setDoc(oldRecord, { ...common, periodStartedAt: Timestamp.fromMillis(approvedAt.toMillis() - 1_000) });
    await setDoc(futureRecord, { ...common, periodStartedAt: Timestamp.fromMillis(approvedAt.toMillis() + 1_000) });

    await assert.rejects(getDoc(doc(trusted.firestore, oldRecord.path)));
    assert.equal((await getDoc(doc(trusted.firestore, futureRecord.path))).exists(), true);

    const visible = await getDocs(query(
      collection(trusted.firestore, "users", driver.uid, "stageSyncRecords"),
      where("driverUserId", "==", driver.uid),
      where("periodStartedAt", ">=", approvedAt),
    ));
    assert.deepEqual(visible.docs.map((item) => item.id), ["future"]);

    await updateDoc(doc(driver.firestore, "trustedContactConnections", connectionId), {
      status: "REVOKED",
      revokedAt: serverTimestamp(),
    });
    await assert.rejects(getDoc(doc(trusted.firestore, futureRecord.path)));
  });
});
