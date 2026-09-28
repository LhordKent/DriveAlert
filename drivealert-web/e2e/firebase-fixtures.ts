import { deleteApp, getApps, initializeApp } from 'firebase/app'
import { connectAuthEmulator, createUserWithEmailAndPassword, getAuth } from 'firebase/auth'
import { connectFirestoreEmulator, doc, getFirestore, serverTimestamp, setDoc } from 'firebase/firestore'

const PROJECT_ID = 'drivealert-lhordkent'
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
    fetch(`http://127.0.0.1:8080/emulator/v1/projects/${PROJECT_ID}/databases/(default)/documents`, { method: 'DELETE' }),
    fetch(`http://127.0.0.1:9099/emulator/v1/projects/${PROJECT_ID}/accounts`, { method: 'DELETE' }),
  ])
}

export async function disposeFixtureApps() {
  await Promise.all(getApps().filter((app) => app.name.startsWith('e2e-')).map(deleteApp))
}

export async function createEmulatorUser(name: string, role: 'DRIVER' | 'TRUSTED_CONTACT', code: string): Promise<EmulatorUser> {
  const index = ++clientCounter
  const app = initializeApp({ projectId: PROJECT_ID, apiKey: 'demo-key', appId: `e2e-${index}` }, `e2e-${index}`)
  const auth = getAuth(app)
  connectAuthEmulator(auth, 'http://127.0.0.1:9099', { disableWarnings: true })
  const firestore = getFirestore(app)
  connectFirestoreEmulator(firestore, '127.0.0.1', 8080)
  const email = `${name.toLowerCase()}-${index}@example.com`
  const password = 'Password123!'
  const credential = await createUserWithEmailAndPassword(auth, email, password)
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
  const collectionUrl = `http://127.0.0.1:8080/v1/projects/${PROJECT_ID}/databases/(default)/documents/trustedContactConnections`
  const listResponse = await fetch(collectionUrl, { headers: { Authorization: 'Bearer owner' } })
  if (!listResponse.ok) throw new Error(`Unable to inspect connection fixtures: ${listResponse.status}`)
  const listed = await listResponse.json() as { documents?: Array<{ name: string; fields?: Record<string, { stringValue?: string }> }> }
  const relationship = listed.documents?.find((item) => item.fields?.driverUserId?.stringValue === driver.uid && item.fields?.trustedContactUserId?.stringValue === trusted.uid)
  if (!relationship) throw new Error('The Trusted Contact request was not persisted before Driver approval.')
  const connectionId = relationship.name.split('/').at(-1) as string
  if (connectionId !== relationshipId(driver, trusted)) throw new Error(`Unexpected relationship ID: ${connectionId}`)
  const approvedAt = Date.now()
  const documentUrl = `http://127.0.0.1:8080/v1/projects/${PROJECT_ID}/databases/(default)/documents/trustedContactConnections/${connectionId}`
  const response = await fetch(`${documentUrl}?updateMask.fieldPaths=status&updateMask.fieldPaths=approvedAt`, {
    method: 'PATCH',
    headers: { Authorization: 'Bearer owner', 'Content-Type': 'application/json' },
    body: JSON.stringify({ fields: { status: { stringValue: 'APPROVED' }, approvedAt: { timestampValue: new Date(approvedAt).toISOString() } } }),
  })
  if (!response.ok) throw new Error(`Unable to simulate Driver approval: ${response.status} ${await response.text()}`)
  const updated = await response.json() as { fields?: Record<string, { stringValue?: string }> }
  if (updated.fields?.driverUserId?.stringValue !== driver.uid || updated.fields?.trustedContactUserId?.stringValue !== trusted.uid) {
    throw new Error(`Driver approval fixture returned an incomplete relationship: ${JSON.stringify(updated)}`)
  }
  return { connectionId, approvedAt }
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
