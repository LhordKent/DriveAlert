const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const { initializeApp, deleteApp } = require("firebase/app");
const { getAuth, connectAuthEmulator, createUserWithEmailAndPassword } = require("firebase/auth");
const { getFirestore, connectFirestoreEmulator, doc, collection, getDoc, getDocs, setDoc, updateDoc, deleteDoc,
  query, where, runTransaction, writeBatch, serverTimestamp } = require("firebase/firestore");
const PROJECT = "drivealert-lhordkent";
const FIRESTORE = process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8080";
const AUTH = process.env.FIREBASE_AUTH_EMULATOR_HOST || "127.0.0.1:9099";
const DEVICE = "AABBCCDDEEFF";
const KEY = "AB".repeat(32);
const apps = [];
let counter = 0;
const accountId = uid => crypto.createHash("sha256").update(uid).digest("hex").toUpperCase().slice(0, 32);

async function client(name, role = "DRIVER") {
  const app = initializeApp({ projectId: PROJECT, apiKey: "demo-key", appId: `device-test-${++counter}` }, `device-test-${counter}`);
  apps.push(app);
  const auth = getAuth(app); connectAuthEmulator(auth, `http://${AUTH}`, { disableWarnings: true });
  const db = getFirestore(app); connectFirestoreEmulator(db, FIRESTORE.split(":")[0], Number(FIRESTORE.split(":")[1]));
  const user = await createUserWithEmailAndPassword(auth, `${name}${counter}@example.com`, "Password123!");
  await require("./verify-email").verifyEmail(auth, user.user);
  const uid = user.user.uid;
  const code = `DA${crypto.randomBytes(26).map(n => "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".charCodeAt(n % 31)).toString()}`;
  await setDoc(doc(db, "users", uid), { uid, firstName: name, lastName: "Test", userRole: role, accountStatus: "ACTIVE", registeredAt: serverTimestamp(), connectionCode: code });
  await setDoc(doc(db, "connectionCodes", code), { ownerUserId: uid, displayName: name, createdAt: serverTimestamp(), active: true });
  return { app, db, uid, code, name };
}
function credential(owner, driver, serial = 0) {
  return { deviceId: DEVICE, ownerUserId: owner.uid, driverUserId: driver.uid, accountId: accountId(driver.uid),
    secret: KEY, kind: serial === 0 ? "OWNER" : "DRIVER", serial, certificate: serial === 0 ? "" : "CD".repeat(32), createdAt: serverTimestamp() };
}
async function claim(owner) {
  const batch = writeBatch(owner.db);
  batch.set(doc(owner.db, "devices", DEVICE), { deviceId: DEVICE, claimed: true, createdAt: serverTimestamp(), schemaVersion: 1 });
  batch.set(doc(owner.db, "deviceOwnership", DEVICE), { deviceId: DEVICE, ownerUserId: owner.uid, displayName: "Family DriveAlert", createdAt: serverTimestamp(), schemaVersion: 1 });
  batch.set(doc(owner.db, "deviceCredentials", `${DEVICE}__${owner.uid}`), credential(owner, owner));
  return batch.commit();
}
async function invite(owner, driver, operation = "activation-1") {
  const membership = doc(owner.db, "deviceDriverAccess", `${DEVICE}__${driver.uid}`);
  const event = doc(owner.db, "users", owner.uid, "deviceSubscriptionEvents", operation);
  await runTransaction(owner.db, async tx => {
    const old = await tx.get(membership);
    const existingEvent = await tx.get(event);
    if (existingEvent.exists()) return;
    if (["PENDING", "ACTIVE"].includes(old.data()?.status)) throw new Error("Already invited");
    const revision = (old.data()?.revision || 0) + 1;
    tx.set(event, { deviceId: DEVICE, ownerUserId: owner.uid, driverUserId: driver.uid, mode: "SIMULATED", kind: "ACTIVATION", createdAt: serverTimestamp() });
    tx.set(membership, { deviceId: DEVICE, ownerUserId: owner.uid, ownerDisplayName: owner.name, driverUserId: driver.uid, driverDisplayName: driver.name,
      deviceDisplayName: "Family DriveAlert", status: "PENDING", revision, targetConnectionCode: driver.code,
      requestedAt: serverTimestamp(), acceptedAt: null, endedAt: null, billingMode: "SIMULATED", demoActivationId: operation });
    tx.set(doc(owner.db, "deviceCredentials", `${DEVICE}__${driver.uid}`), credential(owner, driver, revision));
  });
}
async function transition(actor, driver, status) {
  const ref = doc(actor.db, "deviceDriverAccess", `${DEVICE}__${driver.uid}`);
  await runTransaction(actor.db, async tx => {
    const current = await tx.get(ref);
    tx.update(ref, { status, revision: current.data().revision + 1, [status === "ACTIVE" ? "acceptedAt" : "endedAt"]: serverTimestamp() });
  });
}

describe("DriveAlert device ownership and simulated subscriptions", () => {
  beforeEach(async () => {
    await fetch(`http://${FIRESTORE}/emulator/v1/projects/${PROJECT}/databases/(default)/documents`, { method: "DELETE" });
    await fetch(`http://${AUTH}/emulator/v1/projects/${PROJECT}/accounts`, { method: "DELETE" });
  });
  afterEach(async () => Promise.all(apps.splice(0).map(deleteApp)));

  it("claims atomically with private credentials and exposes only a minimal public marker", async () => {
    const owner = await client("Owner"), stranger = await client("Stranger"); await claim(owner);
    assert.deepEqual(Object.keys((await getDoc(doc(stranger.db, "devices", DEVICE))).data()).sort(), ["claimed", "createdAt", "deviceId", "schemaVersion"]);
    await assert.rejects(getDoc(doc(stranger.db, "deviceOwnership", DEVICE)));
    await assert.rejects(getDoc(doc(stranger.db, "deviceCredentials", `${DEVICE}__${owner.uid}`)));
    assert.equal((await getDoc(doc(owner.db, "deviceCredentials", `${DEVICE}__${owner.uid}`))).data().secret, KEY);
    await assert.rejects(getDocs(collection(owner.db, "devices")));
  });
  it("rejects orphan claim markers, ownership records, and owner secrets", async () => {
    const owner = await client("Owner");
    await assert.rejects(setDoc(doc(owner.db, "devices", DEVICE), { deviceId: DEVICE, claimed: true, createdAt: serverTimestamp(), schemaVersion: 1 }));
    await assert.rejects(setDoc(doc(owner.db, "deviceOwnership", DEVICE), { deviceId: DEVICE, ownerUserId: owner.uid, displayName: "D", createdAt: serverTimestamp(), schemaVersion: 1 }));
    await assert.rejects(setDoc(doc(owner.db, "deviceCredentials", `${DEVICE}__${owner.uid}`), credential(owner, owner)));
  });
  it("allows exactly one simultaneous owner and prevents ownership/key replacement", async () => {
    const a = await client("A"), b = await client("B");
    const results = await Promise.allSettled([claim(a), claim(b)]);
    assert.equal(results.filter(r => r.status === "fulfilled").length, 1);
    const winner = results[0].status === "fulfilled" ? a : b;
    await assert.rejects(updateDoc(doc(winner.db, "deviceOwnership", DEVICE), { ownerUserId: winner === a ? b.uid : a.uid }));
    await assert.rejects(updateDoc(doc(winner.db, "deviceCredentials", `${DEVICE}__${winner.uid}`), { secret: "EF".repeat(32) }));
    await assert.rejects(deleteDoc(doc(winner.db, "devices", DEVICE)));
  });
  it("supports missing-document reads used by owner/recipient transactions", async () => {
    const owner = await client("Owner"), driver = await client("Driver"); await claim(owner);
    assert.equal((await getDoc(doc(owner.db, "deviceDriverAccess", `${DEVICE}__${driver.uid}`))).exists(), false);
    assert.equal((await getDoc(doc(driver.db, "deviceDriverAccess", `${DEVICE}__${driver.uid}`))).exists(), false);
    assert.equal((await getDoc(doc(owner.db, "users", owner.uid, "deviceSubscriptionEvents", "new-op"))).exists(), false);
  });
  it("creates a single demo activation on retries and hides credentials until acceptance", async () => {
    const owner = await client("Owner"), driver = await client("Driver"); await claim(owner); await invite(owner, driver); await invite(owner, driver);
    assert.equal((await getDoc(doc(owner.db, "deviceDriverAccess", `${DEVICE}__${driver.uid}`))).data().revision, 1);
    await assert.rejects(getDoc(doc(driver.db, "deviceCredentials", `${DEVICE}__${driver.uid}`)));
    await transition(driver, driver, "ACTIVE");
    assert.equal((await getDoc(doc(driver.db, "deviceCredentials", `${DEVICE}__${driver.uid}`))).data().kind, "DRIVER");
  });
  it("does not allow an audit event without its matching invitation", async () => {
    const owner = await client("Owner"), driver = await client("Driver"); await claim(owner);
    await assert.rejects(setDoc(doc(owner.db, "users", owner.uid, "deviceSubscriptionEvents", "orphan"), {
      deviceId: DEVICE, ownerUserId: owner.uid, driverUserId: driver.uid, mode: "SIMULATED", kind: "ACTIVATION", createdAt: serverTimestamp() }));
  });
  it("prevents outsiders and the owner from accepting for the target", async () => {
    const owner = await client("Owner"), driver = await client("Driver"), outsider = await client("Outsider");
    await claim(owner); await invite(owner, driver);
    await assert.rejects(transition(owner, driver, "ACTIVE"));
    await assert.rejects(transition(outsider, driver, "ACTIVE"));
    await transition(driver, driver, "ACTIVE");
  });
  it("requires Driver capability and active account status on acceptance", async () => {
    const owner = await client("Owner"), driver = await client("Trusted", "TRUSTED_CONTACT"); await claim(owner); await invite(owner, driver);
    await assert.rejects(transition(driver, driver, "ACTIVE"));
    await updateDoc(doc(driver.db, "users", driver.uid), { userRole: "BOTH", accountStatus: "DEACTIVATED" });
    await assert.rejects(transition(driver, driver, "ACTIVE"));
    await updateDoc(doc(driver.db, "users", driver.uid), { accountStatus: "ACTIVE" });
    await transition(driver, driver, "ACTIVE");
  });
  it("preserves terminal states against stale acceptance and permits a fresh activation", async () => {
    const owner = await client("Owner"), driver = await client("Driver"); await claim(owner); await invite(owner, driver);
    await transition(owner, driver, "CANCELLED");
    await assert.rejects(transition(driver, driver, "ACTIVE"));
    await invite(owner, driver, "activation-2"); await transition(driver, driver, "ACTIVE");
    assert.equal((await getDoc(doc(driver.db, "deviceCredentials", `${DEVICE}__${driver.uid}`))).data().serial, 3);
    await transition(owner, driver, "REMOVED");
    await assert.rejects(getDoc(doc(driver.db, "deviceCredentials", `${DEVICE}__${driver.uid}`)));
    await assert.rejects(transition(driver, driver, "ACTIVE"));
  });
  it("allows recipient decline/leave but not deletion or identity/revision forgery", async () => {
    const owner = await client("Owner"), driver = await client("Driver"); await claim(owner); await invite(owner, driver);
    const ref = doc(driver.db, "deviceDriverAccess", `${DEVICE}__${driver.uid}`);
    await assert.rejects(updateDoc(ref, { status: "ACTIVE", revision: 99, acceptedAt: serverTimestamp() }));
    await assert.rejects(updateDoc(ref, { ownerUserId: driver.uid }));
    await transition(driver, driver, "DECLINED"); await invite(owner, driver, "again");
    await transition(driver, driver, "ACTIVE"); await transition(driver, driver, "LEFT");
    await assert.rejects(deleteDoc(ref));
  });
  it("matches the exact management queries and denies collection-wide access", async () => {
    const owner = await client("Owner"), driver = await client("Driver"), outsider = await client("Other"); await claim(owner); await invite(owner, driver);
    assert.equal((await getDocs(query(collection(owner.db, "deviceOwnership"), where("ownerUserId", "==", owner.uid)))).size, 1);
    assert.equal((await getDocs(query(collection(owner.db, "deviceDriverAccess"), where("ownerUserId", "==", owner.uid)))).size, 1);
    assert.equal((await getDocs(query(collection(driver.db, "deviceDriverAccess"), where("driverUserId", "==", driver.uid)))).size, 1);
    await assert.rejects(getDocs(collection(outsider.db, "deviceDriverAccess")));
    await assert.rejects(getDocs(collection(owner.db, "deviceCredentials")));
  });
  it("keeps demo activation events immutable", async () => {
    const owner = await client("Owner"), driver = await client("Driver"); await claim(owner); await invite(owner, driver);
    const event = doc(owner.db, "users", owner.uid, "deviceSubscriptionEvents", "activation-1");
    await assert.rejects(updateDoc(event, { mode: "PAID" })); await assert.rejects(deleteDoc(event));
    await assert.rejects(getDoc(doc(driver.db, "users", owner.uid, "deviceSubscriptionEvents", "activation-1")));
  });
});
