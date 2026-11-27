import { deleteApp, getApps, initializeApp } from 'firebase/app'
import { connectAuthEmulator, createUserWithEmailAndPassword, getAuth, reload } from 'firebase/auth'
import { connectFirestoreEmulator, doc, getDocFromServer, getFirestore, serverTimestamp, setDoc, updateDoc } from 'firebase/firestore'

const PROJECT_ID = 'drivealert-lhordkent'
const FIRESTORE_ADDRESS = process.env.FIRESTORE_EMULATOR_HOST || '127.0.0.1:8080'
const AUTH_ADDRESS = process.env.FIREBASE_AUTH_EMULATOR_HOST || '127.0.0.1:9099'
let clientCounter = 0

export interface EmulatorUser {
  uid: string
  email: string
  password: string
  code: string
  name: string
  firestore: ReturnType<typeof getFirestore>
}

export async function clearEmulators() {
  await Promise.all([
    fetch(`http://${FIRESTORE_ADDRESS}/emulator/v1/projects/${PROJECT_ID}/databases/(default)/documents`, { method: 'DELETE' }),
    fetch(`http://${AUTH_ADDRESS}/emulator/v1/projects/${PROJECT_ID}/accounts`, { method: 'DELETE' }),
  ])
}

export async function disposeFixtureApps() {
  await Promise.all(getApps().filter((app) => app.name.startsWith('e2e-')).map(deleteApp))
}

export async function createEmulatorUser(
  name: string,
  role: 'DRIVER' | 'TRUSTED_CONTACT',
  code: string,
  password = 'Password123!',
): Promise<EmulatorUser> {
  const index = ++clientCounter
  const app = initializeApp({ projectId: PROJECT_ID, apiKey: 'demo-key', appId: `e2e-${index}` }, `e2e-${index}`)
  const auth = getAuth(app)
  connectAuthEmulator(auth, `http://${AUTH_ADDRESS}`, { disableWarnings: true })
  const firestore = getFirestore(app)
  connectFirestoreEmulator(firestore, '127.0.0.1', Number(FIRESTORE_ADDRESS.split(':').at(-1)))
  const email = `${name.toLowerCase()}-${index}@example.com`
  const credential = await createUserWithEmailAndPassword(auth, email, password)
  const response = await fetch(`http://${AUTH_ADDRESS}/identitytoolkit.googleapis.com/v1/accounts:update`, {
    method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: 'Bearer owner' },
    body: JSON.stringify({ localId: credential.user.uid, emailVerified: true }),
  })
  if (!response.ok) throw new Error('Unable to verify fixture account')
  await reload(credential.user)
  await credential.user.getIdToken(true)
  const fullName = `${name} Tester`
  await setDoc(doc(firestore, 'users', credential.user.uid), {
    uid: credential.user.uid,
    firstName: name,
    lastName: 'Tester',
    email,
    userRole: role,
    accountStatus: 'ACTIVE',
    registeredAt: serverTimestamp(),
    deactivatedAt: null,
    connectionCode: code,
  })
  await setDoc(doc(firestore, 'connectionCodes', code), {
    ownerUserId: credential.user.uid,
    displayName: fullName,
    createdAt: serverTimestamp(),
    active: true,
  })
  return { uid: credential.user.uid, email, password, code, name: fullName, firestore }
}

export function relationshipId(driver: EmulatorUser, trusted: EmulatorUser) {
  return `${driver.uid}__${trusted.uid}`
}

export async function requestFromDriver(driver: EmulatorUser, trusted: EmulatorUser) {
  const connectionId = relationshipId(driver, trusted)
  await setDoc(doc(driver.firestore, 'trustedContactConnections', connectionId), {
    connectionId,
    driverUserId: driver.uid,
    trustedContactUserId: trusted.uid,
    driverName: driver.name,
    driverEmail: driver.email,
    trustedContactName: trusted.name,
    trustedContactEmail: trusted.email,
    requestedByUserId: driver.uid,
    targetConnectionCode: trusted.code,
    status: 'PENDING',
    requestedAt: serverTimestamp(),
    approvedAt: null,
    declinedAt: null,
    revokedAt: null,
  })
  return connectionId
}

export async function approveTrustedRequest(driver: EmulatorUser, trusted: EmulatorUser) {
  const connectionId = relationshipId(driver, trusted)
  const reference = doc(trusted.firestore, 'trustedContactConnections', connectionId)
  await updateDoc(reference, { status: 'APPROVED', approvedAt: serverTimestamp() })
  const approvedAt = (await getDocFromServer(reference)).data()?.approvedAt?.toMillis()
  if (typeof approvedAt !== 'number') throw new Error('Approval timestamp is missing')
  return { connectionId, approvedAt }
}

export async function seedLegacyPendingInvitation(driver: EmulatorUser, trusted: EmulatorUser) {
  const id = await requestFromDriver(driver, trusted)
  const documentUrl = `http://${FIRESTORE_ADDRESS}/v1/projects/${PROJECT_ID}/databases/(default)/documents/trustedContactConnections/${id}`
  const response = await fetch(`${documentUrl}?updateMask.fieldPaths=requestedByUserId&updateMask.fieldPaths=targetConnectionCode`, {
    method: 'PATCH',
    headers: { Authorization: 'Bearer owner', 'Content-Type': 'application/json' },
    body: JSON.stringify({ fields: {
      requestedByUserId: { stringValue: trusted.uid },
      targetConnectionCode: { stringValue: driver.code },
    } }),
  })
  if (!response.ok) throw new Error(`Unable to seed the legacy invitation: ${response.status}`)
}

export async function addStageRecord(driver: EmulatorUser, approvedAt: number, id = 'stage-record-1') {
  await setDoc(doc(driver.firestore, 'users', driver.uid, 'stageSyncRecords', id), {
    stageSyncRecordId: id,
    driverUserId: driver.uid,
    sessionId: 'session-e2e',
    recordType: 'STAGE_3_TRANSITION',
    periodStartedAt: new Date(approvedAt + 1000),
    periodEndedAt: new Date(approvedAt + 61000),
    eventCount: 3,
    signs: ['YAWNING', 'HEAD_NODDING'],
    warningStage: 'STAGE_3',
    warningStageAtDetection: 3,
    syncStatus: 'SYNCED',
    createdAtClient: new Date(approvedAt + 62000),
    uploadedAt: serverTimestamp(),
  })
}
