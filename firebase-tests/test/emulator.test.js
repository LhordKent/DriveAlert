const assert = require("node:assert/strict");
const { initializeApp, deleteApp } = require("firebase/app");
const { connectAuthEmulator, createUserWithEmailAndPassword, getAuth } = require("firebase/auth");
const {
  connectFirestoreEmulator,
  collection,
  doc,
  getDoc,
  getDocs,
  getFirestore,
  serverTimestamp,
  setDoc,
  updateDoc,
} = require("firebase/firestore");

const PROJECT_ID = "drivealert-lhordkent";
let appCounter = 0;

async function clearEmulators() {
  await fetch(`http://127.0.0.1:8080/emulator/v1/projects/${PROJECT_ID}/databases/(default)/documents`, { method: "DELETE" });
  await fetch(`http://127.0.0.1:9099/emulator/v1/projects/${PROJECT_ID}/accounts`, { method: "DELETE" });
}

async function createClient(name, code) {
  const app = initializeApp({ projectId: PROJECT_ID, apiKey: "demo-key", appId: `demo-${++appCounter}` }, `test-${appCounter}`);
  const auth = getAuth(app);
  connectAuthEmulator(auth, "http://127.0.0.1:9099", { disableWarnings: true });
  const firestore = getFirestore(app);
  connectFirestoreEmulator(firestore, "127.0.0.1", 8080);
  const email = `${name.toLowerCase()}@example.com`;
  const credential = await createUserWithEmailAndPassword(auth, email, "Password123!");
  await setDoc(doc(firestore, "users", credential.user.uid), {
    uid: credential.user.uid,
    firstName: name,
    lastName: "Tester",
    email,
    connectionCode: code,
    accountStatus: "ACTIVE",
    registeredAt: serverTimestamp(),
  });
  await setDoc(doc(firestore, "connectionCodes", code), {
    ownerUserId: credential.user.uid,
    displayName: `${name} Tester`,
    createdAt: serverTimestamp(),
    active: true,
  });
  return { app, firestore, uid: credential.user.uid, email, name: `${name} Tester`, code };
}

function requestData(driver, trusted, sender, targetCode) {
  const connectionId = `${driver.uid}__${trusted.uid}`;
  return {
    connectionId,
    driverUserId: driver.uid,
    trustedContactUserId: trusted.uid,
    driverName: driver.name,
    driverEmail: driver.email,
    trustedContactName: trusted.name,
    trustedContactEmail: trusted.email,
    requestedByUserId: sender.uid,
    targetConnectionCode: targetCode,
    status: "PENDING",
    requestedAt: serverTimestamp(),
    approvedAt: null,
    declinedAt: null,
    revokedAt: null,
  };
}

describe("DriveAlert Firestore connection codes", () => {
  const apps = [];
  beforeEach(clearEmulators);
  afterEach(async () => Promise.all(apps.splice(0).map(deleteApp)));

  it("permits exact authenticated lookup but denies code enumeration", async () => {
    const driver = await createClient("Driver", "DA23456789ABCDEFGHJKLMNPQRST");
    const trusted = await createClient("Trusted", "DA3456789ABCDEFGHJKLMNPQRSTU");
    apps.push(driver.app, trusted.app);
    const found = await getDoc(doc(driver.firestore, "connectionCodes", trusted.code));
    assert.equal(found.data().ownerUserId, trusted.uid);
    await assert.rejects(getDocs(collection(driver.firestore, "connectionCodes")));
  });

  it("allows a Driver to request and only the Trusted Contact to approve", async () => {
    const driver = await createClient("Driver", "DA23456789ABCDEFGHJKLMNPQRST");
    const trusted = await createClient("Trusted", "DA3456789ABCDEFGHJKLMNPQRSTU");
    apps.push(driver.app, trusted.app);
    const data = requestData(driver, trusted, driver, trusted.code);
    const ref = doc(driver.firestore, "trustedContactConnections", data.connectionId);
    await setDoc(ref, data);
    await assert.rejects(updateDoc(ref, { status: "APPROVED", approvedAt: serverTimestamp() }));
    await updateDoc(doc(trusted.firestore, "trustedContactConnections", data.connectionId), {
      status: "APPROVED",
      approvedAt: serverTimestamp(),
    });
  });

  it("allows a Trusted Contact to initiate the same relationship", async () => {
    const driver = await createClient("Driver", "DA23456789ABCDEFGHJKLMNPQRST");
    const trusted = await createClient("Trusted", "DA3456789ABCDEFGHJKLMNPQRSTU");
    apps.push(driver.app, trusted.app);
    const data = requestData(driver, trusted, trusted, driver.code);
    await setDoc(doc(trusted.firestore, "trustedContactConnections", data.connectionId), data);
    const stored = await getDoc(doc(driver.firestore, "trustedContactConnections", data.connectionId));
    assert.equal(stored.data().requestedByUserId, trusted.uid);
  });

  it("rejects a request whose target code belongs to neither participant", async () => {
    const driver = await createClient("Driver", "DA23456789ABCDEFGHJKLMNPQRST");
    const trusted = await createClient("Trusted", "DA3456789ABCDEFGHJKLMNPQRSTU");
    const other = await createClient("Other", "DA456789ABCDEFGHJKLMNPQRSTUV");
    apps.push(driver.app, trusted.app, other.app);
    const data = requestData(driver, trusted, driver, other.code);
    await assert.rejects(setDoc(doc(driver.firestore, "trustedContactConnections", data.connectionId), data));
  });
});
