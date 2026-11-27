import { serviceAccessToken, verifyFirebaseToken } from './auth'
import type { Env } from './types'

export const CONNECTION_DETAILS_PATH = '/v1/connection-contact-details'
export const DEVICE_DRIVER_DETAILS_PATH = '/v1/device-driver-details'
const MAX_CONNECTIONS = 20
const DOCUMENT_ID = /^[A-Za-z0-9_-]{1,260}$/
const USER_ID = /^[A-Za-z0-9_-]{1,128}$/
const DEVICE_ACCESS_ID = /^([A-F0-9]{12})__([A-Za-z0-9_-]{1,128})$/
const WEB_ORIGINS = new Set(['https://drivealert.web.app', 'https://drivealert.firebaseapp.com'])

type Fields = Record<string, { stringValue?: string; timestampValue?: string } | undefined>
type Document = { name: string; fields?: Fields }
type BatchRow = { found?: Document; readTime?: string }
type ReadDocuments = (paths: string[], fields: string[], readTime?: string) => Promise<BatchRow[]>

function value(document: Document | undefined, field: string): string {
  return document?.fields?.[field]?.stringValue?.trim() ?? ''
}

async function approvedDetails(uid: string, ids: string[], read: ReadDocuments) {
  const relationships = await read(
    ids.map((id) => 'trustedContactConnections/' + id),
    ['status', 'approvedAt', 'driverUserId', 'trustedContactUserId'],
  )
  const authorized = relationships.flatMap(({ found }) => {
    const driver = value(found, 'driverUserId')
    const contact = value(found, 'trustedContactUserId')
    const id = found?.name.split('/').pop()
    if (!id || !ids.includes(id) || id !== driver + '__' + contact || driver === contact ||
        !USER_ID.test(driver) || !USER_ID.test(contact) || (uid !== driver && uid !== contact) ||
        value(found, 'status') !== 'APPROVED' || !found?.fields?.approvedAt?.timestampValue) return []
    return [{ id, peer: uid === driver ? contact : driver }]
  })
  const byUid = await profilesAt(authorized, relationships, ['email', 'phoneNumber'], read)
  return authorized.map(({ id, peer }) => ({
    connectionId: id,
    email: value(byUid.get(peer), 'email') || null,
    phoneNumber: value(byUid.get(peer), 'phoneNumber') || null,
  }))
}

async function managedDriverDetails(uid: string, ids: string[], read: ReadDocuments) {
  const deviceIds = [...new Set(ids.map((id) => id.slice(0, 12)))]
  const relationships = await read(
    [...deviceIds.map((id) => 'deviceOwnership/' + id), ...ids.map((id) => 'deviceDriverAccess/' + id)],
    ['deviceId', 'ownerUserId', 'driverUserId', 'status'],
  )
  const owned = new Set<string>()
  const access = new Map<string, Document>()
  for (const { found } of relationships) {
    if (!found || value(found, 'ownerUserId') !== uid) continue
    const [collection, id] = found.name.split('/').slice(-2)
    if (collection === 'deviceOwnership' && deviceIds.includes(id) && value(found, 'deviceId') === id) owned.add(id)
    if (collection === 'deviceDriverAccess' && ids.includes(id)) access.set(id, found)
  }
  const authorized = ids.flatMap((id) => {
    const deviceId = id.slice(0, 12)
    const peer = id.slice(14)
    const membership = access.get(id)
    if (!owned.has(deviceId) || peer === uid || value(membership, 'deviceId') !== deviceId ||
        value(membership, 'driverUserId') !== peer || !['PENDING', 'ACTIVE'].includes(value(membership, 'status'))) return []
    return [{ id, peer }]
  })
  const byUid = await profilesAt(authorized, relationships, ['phoneNumber'], read)
  return authorized.map(({ id, peer }) => ({ accessId: id, phoneNumber: value(byUid.get(peer), 'phoneNumber') || null }))
}

async function profilesAt(authorized: { peer: string }[], relationships: BatchRow[], fields: string[], read: ReadDocuments) {
  if (authorized.length === 0) return new Map<string | undefined, Document>()
  // Read profiles at the same snapshot that authorized access to the relationships.
  const readTime = relationships.at(-1)?.readTime
  if (!readTime) throw new Error('FIRESTORE_READ_TIME_MISSING')
  const profiles = await read([...new Set(authorized.map(({ peer }) => 'users/' + peer))], fields, readTime)
  return new Map(profiles.flatMap(({ found }) => found ? [[found.name.split('/').pop(), found]] : []))
}

async function batchRead(env: Env, paths: string[], fields: string[], readTime?: string): Promise<BatchRow[]> {
  const root = `projects/${env.FIREBASE_PROJECT_ID}/databases/(default)/documents`
  const token = await serviceAccessToken(env)
  const response = await fetch(`https://firestore.googleapis.com/v1/${root}:batchGet`, {
    method: 'POST',
    headers: { authorization: `Bearer ${token}`, 'content-type': 'application/json' },
    body: JSON.stringify({ documents: paths.map((path) => `${root}/${path}`), mask: { fieldPaths: fields }, readTime }),
  })
  if (!response.ok) throw new Error('FIRESTORE_READ_FAILED')
  return response.json<BatchRow[]>()
}

export async function connectionDetailsRequest(
  request: Request,
  env: Env,
  verify = verifyFirebaseToken,
  read: ReadDocuments = (paths, fields, readTime) => batchRead(env, paths, fields, readTime),
): Promise<Response> {
  const origin = request.headers.get('origin')
  const headers = new Headers({ 'cache-control': 'no-store', vary: 'Origin' })
  if (origin && !WEB_ORIGINS.has(origin)) {
    return Response.json({ status: 'ORIGIN_NOT_ALLOWED' }, { status: 403, headers })
  }
  if (origin) headers.set('access-control-allow-origin', origin)
  if (request.method === 'OPTIONS') {
    headers.set('access-control-allow-methods', 'POST')
    headers.set('access-control-allow-headers', 'Authorization, Content-Type')
    return new Response(null, { status: 204, headers })
  }
  const json = (status: number, body: object) => Response.json(body, { status, headers })
  if (request.method !== 'POST') return json(405, { status: 'METHOD_NOT_ALLOWED' })

  let uid: string
  try {
    uid = (await verify(request.headers.get('authorization'), env)).uid
  } catch {
    return json(401, { status: 'AUTH_REQUIRED' })
  }
  if (!request.headers.get('content-type')?.toLowerCase().includes('application/json')) {
    return json(400, { status: 'INVALID_REQUEST' })
  }
  const payload: unknown = await request.json().catch(() => null)
  const deviceDrivers = new URL(request.url).pathname === DEVICE_DRIVER_DETAILS_PATH
  const input = payload as { connectionIds?: unknown; accessIds?: unknown } | null
  const ids = deviceDrivers ? input?.accessIds : input?.connectionIds
  const pattern = deviceDrivers ? DEVICE_ACCESS_ID : DOCUMENT_ID
  if (!Array.isArray(ids) || ids.length < 1 || ids.length > MAX_CONNECTIONS ||
      ids.some((id) => typeof id !== 'string' || !pattern.test(id))) {
    return json(400, { status: 'INVALID_REQUEST' })
  }
  try {
    const loadDetails = deviceDrivers ? managedDriverDetails : approvedDetails
    return json(200, { details: await loadDetails(uid, [...new Set<string>(ids)], read) })
  } catch {
    return json(503, { status: 'CONTACT_DETAILS_UNAVAILABLE' })
  }
}
