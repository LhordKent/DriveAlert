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
  query,
  where,
  runTransaction,
  serverTimestamp,
  setDoc,
  updateDoc,
} = require("firebase/firestore");

const PROJECT_ID = "drivealert-lhordkent";
let appCounter = 0;
const { verifyEmail } = require("./verify-email");
const FIRESTORE_ADDRESS = process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8080";
const AUTH_ADDRESS = process.env.FIREBASE_AUTH_EMULATOR_HOST || "127.0.0.1:9099";

async function clearEmulators() {
  await fetch(`http://${FIRESTORE_ADDRESS}/emulator/v1/projects/${PROJECT_ID}/databases/(default)/documents`, { method: "DELETE" });
  await fetch(`http://${AUTH_ADDRESS}/emulator/v1/projects/${PROJECT_ID}/accounts`, { method: "DELETE" });
}

async function createClient(name, code, role = name === "Driver" ? "DRIVER" : "TRUSTED_CONTACT") {
  const app = initializeApp({ projectId: PROJECT_ID, apiKey: "demo-key", appId: `demo-${++appCounter}` }, `test-${appCounter}`);
  const auth = getAuth(app);
  connectAuthEmulator(auth, `http://${AUTH_ADDRESS}`, { disableWarnings: true });
  const firestore = getFirestore(app);
  connectFirestoreEmulator(firestore, FIRESTORE_ADDRESS.split(":")[0], Number(FIRESTORE_ADDRESS.split(":")[1]));
  const email = `${name.toLowerCase()}@example.com`;
  const credential = await createUserWithEmailAndPassword(auth, email, "Password123!");
  await verifyEmail(auth, credential.user);
  await setDoc(doc(firestore, "users", credential.user.uid), {
    uid: credential.user.uid,
    firstName: name,
    lastName: "Tester",
    email,
    connectionCode: code,
    accountStatus: "ACTIVE",
    userRole: role,
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

async function seedLegacyInvitation(driver, trusted, approvedAt = null) {
  const invitation = {
    ...requestData(driver, trusted, trusted, driver.code),
    driverName: "Previous Driver name",
    driverEmail: "",
    requestedAt: new Date(Date.now() - 60_000),
    status: approvedAt ? "APPROVED" : "PENDING",
    approvedAt,
  };
  const fields = Object.fromEntries(Object.entries(invitation).map(([key, value]) => [
    key, value === null ? { nullValue: null } : value instanceof Date
      ? { timestampValue: value.toISOString() } : { stringValue: value },
  ]));
  const response = await fetch(
    `http://${FIRESTORE_ADDRESS}/v1/projects/${PROJECT_ID}/databases/(default)/documents/trustedContactConnections/${invitation.connectionId}`,
    { method: "PATCH", headers: { Authorization: "Bearer owner", "Content-Type": "application/json" }, body: JSON.stringify({ fields }) },
  );
  assert.equal(response.ok, true, await response.text());
  return invitation.connectionId;
}

function stage3Data(driver, id, overrides = {}) {
  return {
    stageSyncRecordId: id,
    driverUserId: driver.uid,
    sessionId: "session-1",
    recordType: "STAGE_3_TRANSITION",
    periodStartedAt: new Date(),
    periodEndedAt: null,
    eventCount: 1,
    signs: ["YAWNING"],
    warningStage: "STAGE_3",
    warningStageAtDetection: 3,
    syncStatus: "SYNCED",
    createdAtClient: new Date(),
    uploadedAt: serverTimestamp(),
    ...overrides,
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

  it("rejects a Trusted Contact inviting the Driver, including an account with both capabilities", async () => {
    const driver = await createClient("Driver", "DA23456789ABCDEFGHJKLMNPQRST");
    const trusted = await createClient("Trusted", "DA3456789ABCDEFGHJKLMNPQRSTU", "BOTH");
    apps.push(driver.app, trusted.app);
    const data = requestData(driver, trusted, trusted, driver.code);
    const trustedRef = doc(trusted.firestore, "trustedContactConnections", data.connectionId);
    await assert.rejects(setDoc(trustedRef, data), { code: "permission-denied" });
  });

  it("allows a prospective participant to read a missing deterministic connection inside a create transaction", async () => {
    const driver = await createClient("Driver", "DA23456789ABCDEFGHJKLMNPQRST");
    const trusted = await createClient("Trusted", "DA3456789ABCDEFGHJKLMNPQRSTU");
    const other = await createClient("Other", "DA456789ABCDEFGHJKLMNPQRSTUV");
    apps.push(driver.app, trusted.app, other.app);
    const data = requestData(driver, trusted, driver, trusted.code);
    const driverRef = doc(driver.firestore, "trustedContactConnections", data.connectionId);

    await runTransaction(driver.firestore, async (transaction) => {
      const existing = await transaction.get(driverRef);
      assert.equal(existing.exists(), false);
      transaction.set(driverRef, data);
    });

    assert.equal((await getDoc(driverRef)).data().requestedByUserId, driver.uid);
    await assert.rejects(getDoc(doc(other.firestore, "trustedContactConnections", data.connectionId)));
  });

  it("requires the right capabilities and supports BOTH on each side", async () => {
    const driver = await createClient("Driver", "DA23456789ABCDEFGHJKLMNPQRST", "TRUSTED_CONTACT");
    const trusted = await createClient("Trusted", "DA3456789ABCDEFGHJKLMNPQRSTU", "DRIVER");
    apps.push(driver.app, trusted.app);
    const data = requestData(driver, trusted, driver, trusted.code);
    const ref = doc(driver.firestore, "trustedContactConnections", data.connectionId);
    await updateDoc(doc(trusted.firestore, "users", trusted.uid), { userRole: "BOTH" });
    await assert.rejects(setDoc(ref, data), { code: "permission-denied" });
    await updateDoc(doc(trusted.firestore, "users", trusted.uid), { userRole: "DRIVER" });
    await updateDoc(doc(driver.firestore, "users", driver.uid), { userRole: "BOTH" });
    await assert.rejects(setDoc(ref, data), { code: "permission-denied" });
    await updateDoc(doc(trusted.firestore, "users", trusted.uid), { userRole: "BOTH" });
    await setDoc(ref, data);
    await updateDoc(doc(trusted.firestore, "trustedContactConnections", data.connectionId), {
      status: "APPROVED", approvedAt: serverTimestamp(),
    });
    assert.equal((await getDoc(ref)).data().status, "APPROVED");
  });

  it("rejects forged requester identity and self-connection", async () => {
    const driver = await createClient("Driver", "DA23456789ABCDEFGHJKLMNPQRST");
    const trusted = await createClient("Trusted", "DA3456789ABCDEFGHJKLMNPQRSTU");
    apps.push(driver.app, trusted.app);
    const forged = requestData(driver, trusted, trusted, driver.code);
    await assert.rejects(setDoc(doc(driver.firestore, "trustedContactConnections", forged.connectionId), forged));
    const self = requestData(driver, driver, driver, driver.code);
    await assert.rejects(setDoc(doc(driver.firestore, "trustedContactConnections", self.connectionId), self));
  });

  it("rejects a request whose target code belongs to neither participant", async () => {
    const driver = await createClient("Driver", "DA23456789ABCDEFGHJKLMNPQRST");
    const trusted = await createClient("Trusted", "DA3456789ABCDEFGHJKLMNPQRSTU");
    const other = await createClient("Other", "DA456789ABCDEFGHJKLMNPQRSTUV");
    apps.push(driver.app, trusted.app, other.app);
    const data = requestData(driver, trusted, driver, other.code);
    await assert.rejects(setDoc(doc(driver.firestore, "trustedContactConnections", data.connectionId), data));
  });

  it("allows the Trusted Contact to decline and the Driver to re-request with reset timestamps", async () => {
    const driver = await createClient("Driver", "DA23456789ABCDEFGHJKLMNPQRST");
    const trusted = await createClient("Trusted", "DA3456789ABCDEFGHJKLMNPQRSTU");
    apps.push(driver.app, trusted.app);
    const data = requestData(driver, trusted, driver, trusted.code);
    const driverRef = doc(driver.firestore, "trustedContactConnections", data.connectionId);
    await setDoc(driverRef, data);
    await assert.rejects(updateDoc(driverRef, { status: "DECLINED", declinedAt: serverTimestamp() }));
    await updateDoc(doc(trusted.firestore, "trustedContactConnections", data.connectionId), {
      status: "DECLINED", declinedAt: serverTimestamp(),
    });
    await updateDoc(driverRef, {
      status: "PENDING", requestedByUserId: driver.uid, targetConnectionCode: trusted.code,
      requestedAt: serverTimestamp(), approvedAt: null, declinedAt: null, revokedAt: null,
    });
    const stored = (await getDoc(driverRef)).data();
    assert.equal(stored.status, "PENDING");
    assert.equal(stored.approvedAt, null);
    assert.equal(stored.declinedAt, null);
    assert.equal(stored.revokedAt, null);
  });

  it("retires a pending contact-sent request until the Driver sends a new invitation", async () => {
    const driver = await createClient("Driver", "DA23456789ABCDEFGHJKLMNPQRST");
    const trusted = await createClient("Trusted", "DA3456789ABCDEFGHJKLMNPQRSTU");
    apps.push(driver.app, trusted.app);
    const id = await seedLegacyInvitation(driver, trusted);
    const driverRef = doc(driver.firestore, "trustedContactConnections", id);
    const trustedRef = doc(trusted.firestore, "trustedContactConnections", id);
    await assert.rejects(updateDoc(driverRef, { status: "APPROVED", approvedAt: serverTimestamp() }), { code: "permission-denied" });
    await assert.rejects(updateDoc(driverRef, { status: "DECLINED", declinedAt: serverTimestamp() }), { code: "permission-denied" });
    await assert.rejects(updateDoc(trustedRef, { status: "REVOKED", revokedAt: serverTimestamp() }), { code: "permission-denied" });
    const refresh = {
      status: "PENDING", requestedByUserId: driver.uid, targetConnectionCode: trusted.code,
      requestedAt: serverTimestamp(), approvedAt: null, declinedAt: null, revokedAt: null,
      driverName: driver.name, driverEmail: driver.email,
    };
    await assert.rejects(updateDoc(driverRef, { ...refresh, trustedContactName: "Changed" }), { code: "permission-denied" });
    await updateDoc(driverRef, refresh);
    const stored = (await getDoc(trustedRef)).data();
    assert.equal(stored.connectionId, id);
    assert.equal(stored.requestedByUserId, driver.uid);
    assert.equal(stored.targetConnectionCode, trusted.code);
    assert.equal(stored.driverName, driver.name);
    assert.equal(stored.driverEmail, driver.email);
    assert.equal(stored.approvedAt, null);
    await assert.rejects(updateDoc(driverRef, refresh), { code: "permission-denied" });
    await updateDoc(trustedRef, { status: "APPROVED", approvedAt: serverTimestamp() });
  });

  it("allows an approved relationship to be revoked and re-requested by its Driver", async () => {
    const driver = await createClient("Driver", "DA23456789ABCDEFGHJKLMNPQRST");
    const trusted = await createClient("Trusted", "DA3456789ABCDEFGHJKLMNPQRSTU");
    apps.push(driver.app, trusted.app);
    const data = requestData(driver, trusted, driver, trusted.code);
    const driverRef = doc(driver.firestore, "trustedContactConnections", data.connectionId);
    await setDoc(driverRef, data);
    await updateDoc(doc(trusted.firestore, "trustedContactConnections", data.connectionId), {
      status: "APPROVED", approvedAt: serverTimestamp(),
    });
    await updateDoc(driverRef, { status: "REVOKED", revokedAt: serverTimestamp() });
    await updateDoc(driverRef, {
      status: "PENDING", requestedByUserId: driver.uid, targetConnectionCode: trusted.code,
      requestedAt: serverTimestamp(), approvedAt: null, declinedAt: null, revokedAt: null,
    });
    assert.equal((await getDoc(driverRef)).data().status, "PENDING");
  });

  it("allows the Trusted Contact to disconnect but denies a new contact-sent request", async () => {
    const driver = await createClient("Driver", "DA23456789ABCDEFGHJKLMNPQRST");
    const trusted = await createClient("Trusted", "DA3456789ABCDEFGHJKLMNPQRSTU");
    apps.push(driver.app, trusted.app);
    const data = requestData(driver, trusted, driver, trusted.code);
    const trustedRef = doc(trusted.firestore, "trustedContactConnections", data.connectionId);
    await setDoc(doc(driver.firestore, "trustedContactConnections", data.connectionId), data);
    await updateDoc(trustedRef, { status: "APPROVED", approvedAt: serverTimestamp() });
    await updateDoc(trustedRef, { status: "REVOKED", revokedAt: serverTimestamp() });
    await assert.rejects(updateDoc(trustedRef, {
      status: "PENDING", requestedByUserId: trusted.uid, targetConnectionCode: driver.code,
      requestedAt: serverTimestamp(), approvedAt: null, declinedAt: null, revokedAt: null,
    }), { code: "permission-denied" });
    const stored = (await getDoc(trustedRef)).data();
    assert.equal(stored.status, "REVOKED");
    assert.ok(stored.approvedAt);
    assert.ok(stored.revokedAt);
  });

  it("allows only the Driver to cancel and makes a concurrent acceptance or cancel win once", async () => {
    const driver = await createClient("Driver", "DA23456789ABCDEFGHJKLMNPQRST");
    const trusted = await createClient("Trusted", "DA3456789ABCDEFGHJKLMNPQRSTU");
    apps.push(driver.app, trusted.app);
    const data = requestData(driver, trusted, driver, trusted.code);
    const driverRef = doc(driver.firestore, "trustedContactConnections", data.connectionId);
    const trustedRef = doc(trusted.firestore, "trustedContactConnections", data.connectionId);
    await setDoc(driverRef, data);
    await assert.rejects(updateDoc(trustedRef, { status: "REVOKED", revokedAt: serverTimestamp() }), { code: "permission-denied" });
    const results = await Promise.allSettled([
      runTransaction(driver.firestore, async (transaction) => {
        const snapshot = await transaction.get(driverRef);
        assert.equal(snapshot.data().status, "PENDING");
        transaction.update(driverRef, { status: "REVOKED", revokedAt: serverTimestamp() });
      }),
      updateDoc(trustedRef, { status: "APPROVED", approvedAt: serverTimestamp() }),
    ]);
    assert.equal(results.filter((result) => result.status === "fulfilled").length, 1);
    const stored = (await getDoc(driverRef)).data();
    assert.equal(stored.status, results[0].status === "fulfilled" ? "REVOKED" : "APPROVED");
  });

  it("preserves the cutoff for a previously approved contact-sent relationship", async () => {
    const driver = await createClient("Driver", "DA23456789ABCDEFGHJKLMNPQRST");
    const trusted = await createClient("Trusted", "DA3456789ABCDEFGHJKLMNPQRSTU");
    apps.push(driver.app, trusted.app);
    const approvedAt = new Date(Date.now() - 10_000);
    const id = await seedLegacyInvitation(driver, trusted, approvedAt);
    const ref = doc(driver.firestore, "trustedContactConnections", id);
    for (const [name, offset] of [["before", -1_000], ["after", 1_000]]) {
      await setDoc(doc(driver.firestore, "users", driver.uid, "stageSyncRecords", name),
        stage3Data(driver, name, { periodStartedAt: new Date(approvedAt.getTime() + offset) }));
    }
    const readShared = (name) => getDoc(doc(trusted.firestore, "users", driver.uid, "stageSyncRecords", name));
    await assert.rejects(readShared("before"), { code: "permission-denied" });
    assert.equal((await readShared("after")).exists(), true);
    assert.equal((await getDoc(ref)).data().approvedAt.toMillis(), approvedAt.getTime());
    await updateDoc(doc(trusted.firestore, "trustedContactConnections", id), { status: "REVOKED", revokedAt: serverTimestamp() });
    await assert.rejects(readShared("after"), { code: "permission-denied" });
  });

  it("rejects relationship mutation by a non-participant", async () => {
    const driver = await createClient("Driver", "DA23456789ABCDEFGHJKLMNPQRST");
    const trusted = await createClient("Trusted", "DA3456789ABCDEFGHJKLMNPQRSTU");
    const other = await createClient("Other", "DA456789ABCDEFGHJKLMNPQRSTUV");
    apps.push(driver.app, trusted.app, other.app);
    const data = requestData(driver, trusted, driver, trusted.code);
    await setDoc(doc(driver.firestore, "trustedContactConnections", data.connectionId), data);
    await assert.rejects(updateDoc(doc(other.firestore, "trustedContactConnections", data.connectionId), {
      status: "APPROVED", approvedAt: serverTimestamp(),
    }));
  });

  it("keeps one invitation when two sends target the same relationship", async () => {
    const driver = await createClient("Driver", "DA23456789ABCDEFGHJKLMNPQRST");
    const trusted = await createClient("Trusted", "DA3456789ABCDEFGHJKLMNPQRSTU");
    apps.push(driver.app, trusted.app);
    const data = requestData(driver, trusted, driver, trusted.code);
    const reference = doc(driver.firestore, "trustedContactConnections", data.connectionId);
    const send = () => runTransaction(driver.firestore, async (transaction) => {
      assert.equal((await transaction.get(reference)).exists(), false);
      transaction.set(reference, data);
    });
    const results = await Promise.allSettled([send(), send()]);
    assert.equal(results.filter((result) => result.status === "fulfilled").length, 1);
    assert.equal((await getDoc(reference)).data().status, "PENDING");
    await updateDoc(reference, {
      status: "REVOKED", revokedAt: serverTimestamp(),
    });
    await setDoc(reference, requestData(driver, trusted, driver, trusted.code));
    assert.equal((await getDoc(reference)).data().status, "PENDING");
    assert.equal((await getDoc(reference)).data().revokedAt, null);
  });

  it("accepts only authenticated-owner Stage 3 synchronization records", async () => {
    const driver = await createClient("Driver", "DA23456789ABCDEFGHJKLMNPQRST");
    const other = await createClient("Other", "DA456789ABCDEFGHJKLMNPQRSTUV");
    apps.push(driver.app, other.app);
    await setDoc(doc(driver.firestore, "users", driver.uid, "stageSyncRecords", "sync-1"), stage3Data(driver, "sync-1"));
    await assert.rejects(setDoc(
      doc(other.firestore, "users", driver.uid, "stageSyncRecords", "sync-2"),
      stage3Data(driver, "sync-2"),
    ));
    await assert.rejects(setDoc(
      doc(driver.firestore, "users", driver.uid, "stageSyncRecords", "sync-3"),
      stage3Data(driver, "sync-3", { warningStage: "STAGE_2", warningStageAtDetection: 2 }),
    ));
  });

  it("accepts legacy Stage 3 record type values from already-installed Android builds", async () => {
    const driver = await createClient("Driver", "DA23456789ABCDEFGHJKLMNPQRST");
    apps.push(driver.app);
    await setDoc(
      doc(driver.firestore, "users", driver.uid, "stageSyncRecords", "legacy-transition"),
      stage3Data(driver, "legacy-transition", { recordType: "STAGE3_TRANSITION" }),
    );
    await setDoc(
      doc(driver.firestore, "users", driver.uid, "stageSyncRecords", "legacy-persistence"),
      stage3Data(driver, "legacy-persistence", { recordType: "STAGE3_PERSISTENCE" }),
    );
  });

  it("allows an approved Trusted Contact to read an eligible Stage 3 record", async () => {
    const driver = await createClient("Driver", "DA23456789ABCDEFGHJKLMNPQRST");
    const trusted = await createClient("Trusted", "DA3456789ABCDEFGHJKLMNPQRSTU");
    apps.push(driver.app, trusted.app);
    const data = requestData(driver, trusted, driver, trusted.code);
    await setDoc(doc(driver.firestore, "trustedContactConnections", data.connectionId), data);
    const trustedConnection = doc(trusted.firestore, "trustedContactConnections", data.connectionId);
    await updateDoc(trustedConnection, { status: "APPROVED", approvedAt: serverTimestamp() });
    const approvedAt = (await getDoc(trustedConnection)).data().approvedAt.toMillis();
    await setDoc(
      doc(driver.firestore, "users", driver.uid, "stageSyncRecords", "eligible"),
      stage3Data(driver, "eligible", { periodStartedAt: new Date(approvedAt + 1000) }),
    );
    const shared = await getDoc(doc(trusted.firestore, "users", driver.uid, "stageSyncRecords", "eligible"));
    assert.equal(shared.data().stageSyncRecordId, "eligible");
    // Match the Android listener, including its implicit ascending time order.
    const timeline = await getDocs(query(
      collection(trusted.firestore, "users", driver.uid, "stageSyncRecords"),
      where("driverUserId", "==", driver.uid),
      where("periodStartedAt", ">=", new Date(approvedAt)),
    ));
    assert.deepEqual(timeline.docs.map(record => record.id), ["eligible"]);
  });

  it("denies a Trusted Contact access to a pre-approval Stage 3 record", async () => {
    const driver = await createClient("Driver", "DA23456789ABCDEFGHJKLMNPQRST");
    const trusted = await createClient("Trusted", "DA3456789ABCDEFGHJKLMNPQRSTU");
    apps.push(driver.app, trusted.app);
    const data = requestData(driver, trusted, driver, trusted.code);
    await setDoc(doc(driver.firestore, "trustedContactConnections", data.connectionId), data);
    const trustedConnection = doc(trusted.firestore, "trustedContactConnections", data.connectionId);
    await updateDoc(trustedConnection, { status: "APPROVED", approvedAt: serverTimestamp() });
    const approvedAt = (await getDoc(trustedConnection)).data().approvedAt.toMillis();
    await setDoc(
      doc(driver.firestore, "users", driver.uid, "stageSyncRecords", "before-approval"),
      stage3Data(driver, "before-approval", { periodStartedAt: new Date(approvedAt - 1000) }),
    );
    await assert.rejects(getDoc(doc(trusted.firestore, "users", driver.uid, "stageSyncRecords", "before-approval")));
  });

  it("removes shared-record access as soon as the relationship is revoked", async () => {
    const driver = await createClient("Driver", "DA23456789ABCDEFGHJKLMNPQRST");
    const trusted = await createClient("Trusted", "DA3456789ABCDEFGHJKLMNPQRSTU");
    apps.push(driver.app, trusted.app);
    const data = requestData(driver, trusted, driver, trusted.code);
    await setDoc(doc(driver.firestore, "trustedContactConnections", data.connectionId), data);
    const trustedConnection = doc(trusted.firestore, "trustedContactConnections", data.connectionId);
    await updateDoc(trustedConnection, { status: "APPROVED", approvedAt: serverTimestamp() });
    const approvedAt = (await getDoc(trustedConnection)).data().approvedAt.toMillis();
    const driverRecord = doc(driver.firestore, "users", driver.uid, "stageSyncRecords", "revoked-record");
    await setDoc(driverRecord, stage3Data(driver, "revoked-record", { periodStartedAt: new Date(approvedAt + 1000) }));
    assert.equal((await getDoc(doc(trusted.firestore, "users", driver.uid, "stageSyncRecords", "revoked-record"))).exists(), true);
    await updateDoc(trustedConnection, { status: "REVOKED", revokedAt: serverTimestamp() });
    await assert.rejects(getDoc(doc(trusted.firestore, "users", driver.uid, "stageSyncRecords", "revoked-record")));
  });

  it("denies unrelated Trusted Contacts and denies Trusted Contact Stage 3 writes", async () => {
    const driver = await createClient("Driver", "DA23456789ABCDEFGHJKLMNPQRST");
    const trusted = await createClient("Trusted", "DA3456789ABCDEFGHJKLMNPQRSTU");
    const other = await createClient("Other", "DA456789ABCDEFGHJKLMNPQRSTUV");
    apps.push(driver.app, trusted.app, other.app);
    const data = requestData(driver, trusted, driver, trusted.code);
    await setDoc(doc(driver.firestore, "trustedContactConnections", data.connectionId), data);
    const trustedConnection = doc(trusted.firestore, "trustedContactConnections", data.connectionId);
    await updateDoc(trustedConnection, { status: "APPROVED", approvedAt: serverTimestamp() });
    const approvedAt = (await getDoc(trustedConnection)).data().approvedAt.toMillis();
    await setDoc(
      doc(driver.firestore, "users", driver.uid, "stageSyncRecords", "private-record"),
      stage3Data(driver, "private-record", { periodStartedAt: new Date(approvedAt + 1000) }),
    );
    await assert.rejects(getDoc(doc(other.firestore, "users", driver.uid, "stageSyncRecords", "private-record")));
    await assert.rejects(setDoc(
      doc(trusted.firestore, "users", driver.uid, "stageSyncRecords", "forged-by-trusted"),
      stage3Data(driver, "forged-by-trusted", { periodStartedAt: new Date(approvedAt + 2000) }),
    ));
  });

  it("allows only the owner to read and update a trusted Driver view state with server timestamps", async () => {
    const trusted = await createClient("Trusted", "DA3456789ABCDEFGHJKLMNPQRSTU");
    const other = await createClient("Other", "DA456789ABCDEFGHJKLMNPQRSTUV");
    apps.push(trusted.app, other.app);
    const driverId = "driver-1";
    const ownerRef = doc(trusted.firestore, "users", trusted.uid, "trustedDriverStates", driverId);
    assert.equal((await getDoc(ownerRef)).exists(), false);
    await setDoc(ownerRef, {
      driverUserId: driverId,
      lastViewedAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    });
    assert.equal((await getDoc(ownerRef)).data().driverUserId, driverId);
    await updateDoc(ownerRef, { lastViewedAt: serverTimestamp(), updatedAt: serverTimestamp() });
    await assert.rejects(getDoc(doc(other.firestore, "users", trusted.uid, "trustedDriverStates", driverId)));
    await assert.rejects(setDoc(doc(other.firestore, "users", trusted.uid, "trustedDriverStates", driverId), {
      driverUserId: driverId,
      lastViewedAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    }));
    await assert.rejects(setDoc(doc(trusted.firestore, "users", trusted.uid, "trustedDriverStates", "driver-2"), {
      driverUserId: "spoofed-driver",
      lastViewedAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    }));
  });
});
