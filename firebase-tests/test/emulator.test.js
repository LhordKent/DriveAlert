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
  runTransaction,
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

  it("allows a Trusted Contact to request and only the Driver to approve", async () => {
    const driver = await createClient("Driver", "DA23456789ABCDEFGHJKLMNPQRST");
    const trusted = await createClient("Trusted", "DA3456789ABCDEFGHJKLMNPQRSTU");
    apps.push(driver.app, trusted.app);
    const data = requestData(driver, trusted, trusted, driver.code);
    const trustedRef = doc(trusted.firestore, "trustedContactConnections", data.connectionId);
    await setDoc(trustedRef, data);
    assert.equal((await getDoc(trustedRef)).data().requestedByUserId, trusted.uid);
    await assert.rejects(updateDoc(trustedRef, { status: "APPROVED", approvedAt: serverTimestamp() }));
    await updateDoc(doc(driver.firestore, "trustedContactConnections", data.connectionId), {
      status: "APPROVED", approvedAt: serverTimestamp(),
    });
  });

  it("allows a prospective participant to read a missing deterministic connection inside a create transaction", async () => {
    const driver = await createClient("Driver", "DA23456789ABCDEFGHJKLMNPQRST");
    const trusted = await createClient("Trusted", "DA3456789ABCDEFGHJKLMNPQRSTU");
    const other = await createClient("Other", "DA456789ABCDEFGHJKLMNPQRSTUV");
    apps.push(driver.app, trusted.app, other.app);
    const data = requestData(driver, trusted, trusted, driver.code);
    const trustedRef = doc(trusted.firestore, "trustedContactConnections", data.connectionId);

    await runTransaction(trusted.firestore, async (transaction) => {
      const existing = await transaction.get(trustedRef);
      assert.equal(existing.exists(), false);
      transaction.set(trustedRef, data);
    });

    assert.equal((await getDoc(trustedRef)).data().requestedByUserId, trusted.uid);
    await assert.rejects(getDoc(doc(other.firestore, "trustedContactConnections", data.connectionId)));
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

  it("allows the Driver to decline a Trusted Contact request and reverses direction on re-request", async () => {
    const driver = await createClient("Driver", "DA23456789ABCDEFGHJKLMNPQRST");
    const trusted = await createClient("Trusted", "DA3456789ABCDEFGHJKLMNPQRSTU");
    apps.push(driver.app, trusted.app);
    const data = requestData(driver, trusted, trusted, driver.code);
    const trustedRef = doc(trusted.firestore, "trustedContactConnections", data.connectionId);
    await setDoc(trustedRef, data);
    await assert.rejects(updateDoc(trustedRef, { status: "DECLINED", declinedAt: serverTimestamp() }));
    await updateDoc(doc(driver.firestore, "trustedContactConnections", data.connectionId), {
      status: "DECLINED", declinedAt: serverTimestamp(),
    });
    await updateDoc(doc(driver.firestore, "trustedContactConnections", data.connectionId), {
      status: "PENDING", requestedByUserId: driver.uid, targetConnectionCode: trusted.code,
      requestedAt: serverTimestamp(), approvedAt: null, declinedAt: null, revokedAt: null,
    });
    const stored = (await getDoc(trustedRef)).data();
    assert.equal(stored.connectionId, `${driver.uid}__${trusted.uid}`);
    assert.equal(stored.requestedByUserId, driver.uid);
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

  it("allows either participant to revoke and the Trusted Contact to re-request after revocation", async () => {
    const driver = await createClient("Driver", "DA23456789ABCDEFGHJKLMNPQRST");
    const trusted = await createClient("Trusted", "DA3456789ABCDEFGHJKLMNPQRSTU");
    apps.push(driver.app, trusted.app);
    const data = requestData(driver, trusted, driver, trusted.code);
    const trustedRef = doc(trusted.firestore, "trustedContactConnections", data.connectionId);
    await setDoc(doc(driver.firestore, "trustedContactConnections", data.connectionId), data);
    await updateDoc(trustedRef, { status: "APPROVED", approvedAt: serverTimestamp() });
    await updateDoc(trustedRef, { status: "REVOKED", revokedAt: serverTimestamp() });
    await updateDoc(trustedRef, {
      status: "PENDING", requestedByUserId: trusted.uid, targetConnectionCode: driver.code,
      requestedAt: serverTimestamp(), approvedAt: null, declinedAt: null, revokedAt: null,
    });
    const stored = (await getDoc(trustedRef)).data();
    assert.equal(stored.requestedByUserId, trusted.uid);
    assert.equal(stored.approvedAt, null);
    assert.equal(stored.revokedAt, null);
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

  it("allows only the requester to cancel a pending request in either direction", async () => {
    const driver = await createClient("Driver", "DA23456789ABCDEFGHJKLMNPQRST");
    const trusted = await createClient("Trusted", "DA3456789ABCDEFGHJKLMNPQRSTU");
    apps.push(driver.app, trusted.app);
    const data = requestData(driver, trusted, driver, trusted.code);
    await setDoc(doc(driver.firestore, "trustedContactConnections", data.connectionId), data);
    await assert.rejects(updateDoc(doc(trusted.firestore, "trustedContactConnections", data.connectionId), {
      status: "REVOKED", revokedAt: serverTimestamp(),
    }));
    await updateDoc(doc(driver.firestore, "trustedContactConnections", data.connectionId), {
      status: "REVOKED", revokedAt: serverTimestamp(),
    });

    const reverse = requestData(driver, trusted, trusted, driver.code);
    await updateDoc(doc(trusted.firestore, "trustedContactConnections", data.connectionId), {
      status: "PENDING", requestedByUserId: trusted.uid, targetConnectionCode: driver.code,
      requestedAt: serverTimestamp(), approvedAt: null, declinedAt: null, revokedAt: null,
    });
    await assert.rejects(updateDoc(doc(driver.firestore, "trustedContactConnections", data.connectionId), {
      status: "REVOKED", revokedAt: serverTimestamp(),
    }));
    await updateDoc(doc(trusted.firestore, "trustedContactConnections", reverse.connectionId), {
      status: "REVOKED", revokedAt: serverTimestamp(),
    });
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
});
