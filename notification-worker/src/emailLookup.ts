import { emailLookupAccessToken, serviceAccessToken } from './auth'
import type { AuthenticatedUser, Env } from './types'

const LOOKUP_WINDOW_MS = 5 * 60_000
const MAX_SEARCHES_PER_WINDOW = 10
const MATCH_LIFETIME_MS = 5 * 60_000
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const UID = /^[A-Za-z0-9_-]{1,128}$/
const CONNECTION_CODE = /^DA[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{26}$/
const MATCH_ID = /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/
const DEVICE_ID = /^[A-F0-9]{12}$/

type Field = { stringValue?: string; booleanValue?: boolean; timestampValue?: string; nullValue?: null }
type Fields = Record<string, Field>
type Document = { updateTime: string; fields: Fields }
type FirebaseAccount = { localId?: string; email?: string; emailVerified?: boolean; disabled?: boolean }
type Match = { trusted_uid: string; email_fingerprint: string; connection_version: string | null }
type DeviceMatch = { target_uid: string; email_fingerprint: string }
type LookupAction = 'lookup' | 'invite' | 'device-lookup' | 'device-resolve'
type LookupInput = { email?: unknown; matchId?: unknown; deviceId?: unknown }

function response(status: number, value: Record<string, unknown>): Response {
  return Response.json(value, { status, headers: { 'cache-control': 'no-store' } })
}

function text(fields: Fields, key: string): string { return fields[key]?.stringValue ?? '' }
function stringValue(value: string): Field { return { stringValue: value } }
function displayName(fields: Fields): string {
  return ['firstName', 'middleName', 'lastName', 'suffix'].map((key) => text(fields, key).trim()).filter(Boolean).join(' ') || 'DriveAlert user'
}

function activeProfile(profile: Document | null, role: 'DRIVER' | 'TRUSTED_CONTACT'): profile is Document {
  if (!profile) return false
  const value = text(profile.fields, 'userRole')
  const status = text(profile.fields, 'accountStatus')
  return (value === role || value === 'BOTH') && (status === '' || status === 'ACTIVE') && !profile.fields.deactivatedAt?.timestampValue
}

function firestoreRoot(env: Env): string {
  return `projects/${env.FIREBASE_PROJECT_ID}/databases/(default)/documents`
}

async function readDocument(env: Env, path: string): Promise<Document | null> {
  const token = await serviceAccessToken(env)
  const result = await fetch(`https://firestore.googleapis.com/v1/${firestoreRoot(env)}/${path}`, {
    headers: { authorization: `Bearer ${token}` },
  })
  if (result.status === 404) return null
  if (!result.ok) throw new Error('EMAIL_LOOKUP_UNAVAILABLE')
  return result.json<Document>()
}

async function firebaseAccount(env: Env, filter: { email: string[] } | { localId: string[] }): Promise<FirebaseAccount | null> {
  const token = await emailLookupAccessToken(env)
  const result = await fetch(`https://identitytoolkit.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/accounts:lookup`, {
    method: 'POST',
    headers: { authorization: `Bearer ${token}`, 'content-type': 'application/json' },
    body: JSON.stringify(filter),
  })
  if (!result.ok) {
    const failure = await result.json<{ error?: { message?: string } }>().catch(() => null)
    if (['EMAIL_NOT_FOUND', 'USER_NOT_FOUND'].includes(failure?.error?.message ?? '')) return null
    throw new Error('EMAIL_LOOKUP_UNAVAILABLE')
  }
  const { users } = await result.json<{ users?: FirebaseAccount[] }>()
  const account = users?.length === 1 ? users[0] : null
  return account && UID.test(account.localId ?? '') && account.email && account.emailVerified === true && account.disabled !== true
    ? account : null
}

async function emailFingerprint(email: string): Promise<string> {
  const hash = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(email.toLowerCase()))
  return Array.from(new Uint8Array(hash), (byte) => byte.toString(16).padStart(2, '0')).join('')
}

async function acceptSearch(env: Env, driverUid: string, now: number): Promise<boolean> {
  const expired = now - LOOKUP_WINDOW_MS
  const row = await env.DB.prepare(`INSERT INTO email_lookup_limits(driver_uid,window_started_at,searches)
      VALUES(?,?,1) ON CONFLICT(driver_uid) DO UPDATE SET
      searches=CASE WHEN window_started_at<=? THEN 1 ELSE searches+1 END,
      window_started_at=CASE WHEN window_started_at<=? THEN excluded.window_started_at ELSE window_started_at END
      WHERE window_started_at<=? OR searches<?
      RETURNING searches`)
    .bind(driverUid, now, expired, expired, expired, MAX_SEARCHES_PER_WINDOW).first<{ searches: number }>()
  return row != null
}

async function eligibleTarget(env: Env, uid: string, role: 'DRIVER' | 'TRUSTED_CONTACT'): Promise<{ code: string; name: string } | null> {
  const profile = await readDocument(env, `users/${uid}`)
  if (!activeProfile(profile, role)) return null
  const code = text(profile.fields, 'connectionCode')
  if (!CONNECTION_CODE.test(code)) return null
  const entry = await readDocument(env, `connectionCodes/${code}`)
  if (!entry || entry.fields.active?.booleanValue !== true || text(entry.fields, 'ownerUserId') !== uid) return null
  return { code, name: displayName(profile.fields) }
}

async function findContact(env: Env, user: AuthenticatedUser, now: number, value: unknown): Promise<Response> {
  const email = typeof value === 'string' ? value.trim().toLowerCase() : ''
  if (!EMAIL.test(email) || email.length > 254) return response(400, { status: 'INVALID_EMAIL' })
  if (!await acceptSearch(env, user.uid, now)) return response(429, { status: 'EMAIL_SEARCH_LIMIT' })
  const account = await firebaseAccount(env, { email: [email] })
  if (!account?.localId || !account.email || account.email.toLowerCase() !== email || account.localId === user.uid) {
    return response(404, { status: 'NO_EMAIL_MATCH' })
  }
  const contact = await eligibleTarget(env, account.localId, 'TRUSTED_CONTACT')
  if (!contact) return response(404, { status: 'NO_EMAIL_MATCH' })

  const connection = await readDocument(env, `trustedContactConnections/${user.uid}__${account.localId}`)
  const matchId = crypto.randomUUID()
  await env.DB.prepare(`INSERT INTO email_lookup_matches
      (match_id,driver_uid,trusted_uid,email_fingerprint,connection_version,expires_at) VALUES(?,?,?,?,?,?)`)
    .bind(matchId, user.uid, account.localId, await emailFingerprint(email), connection?.updateTime ?? null, now + MATCH_LIFETIME_MS).run()
  return response(200, { status: 'EMAIL_MATCH', displayName: contact.name, email: account.email, matchId })
}

async function sendInvitation(
  request: Request, env: Env, user: AuthenticatedUser, driver: Document, now: number, value: unknown,
): Promise<Response> {
  const matchId = typeof value === 'string' ? value : ''
  if (!MATCH_ID.test(matchId)) return response(400, { status: 'INVALID_EMAIL_MATCH' })
  const match = await env.DB.prepare(`SELECT trusted_uid,email_fingerprint,connection_version FROM email_lookup_matches
      WHERE match_id=? AND driver_uid=? AND expires_at>?`).bind(matchId, user.uid, now).first<Match>()
  if (!match) return response(404, { status: 'EMAIL_MATCH_EXPIRED' })

  const account = await firebaseAccount(env, { localId: [match.trusted_uid] })
  if (!account?.email || account.localId !== match.trusted_uid || await emailFingerprint(account.email) !== match.email_fingerprint) {
    return response(404, { status: 'EMAIL_MATCH_EXPIRED' })
  }
  const contact = await eligibleTarget(env, match.trusted_uid, 'TRUSTED_CONTACT')
  if (!contact) return response(404, { status: 'EMAIL_MATCH_EXPIRED' })

  const connectionId = `${user.uid}__${match.trusted_uid}`
  const path = `trustedContactConnections/${connectionId}`
  const current = await readDocument(env, path)
  const status = current ? text(current.fields, 'status') : ''
  const oldContactInvitation = current && text(current.fields, 'requestedByUserId') === match.trusted_uid
  if (status === 'APPROVED' || (status === 'PENDING' && !oldContactInvitation)) {
    return response(409, { status: 'INVITATION_EXISTS' })
  }
  if ((current?.updateTime ?? null) !== match.connection_version) return response(409, { status: 'EMAIL_MATCH_CHANGED' })

  const pending: Fields = {
    requestedByUserId: stringValue(user.uid),
    targetConnectionCode: stringValue(contact.code),
    status: stringValue('PENDING'),
    approvedAt: { nullValue: null },
    declinedAt: { nullValue: null },
    revokedAt: { nullValue: null },
  }
  if (!current || oldContactInvitation) {
    pending.driverName = stringValue(displayName(driver.fields))
    pending.driverEmail = stringValue(user.email ?? '')
  }
  if (!current) {
    pending.connectionId = stringValue(connectionId)
    pending.driverUserId = stringValue(user.uid)
    pending.trustedContactUserId = stringValue(match.trusted_uid)
    pending.trustedContactName = stringValue(contact.name)
    pending.trustedContactEmail = stringValue('')
  }
  const root = firestoreRoot(env)
  const result = await fetch(`https://firestore.googleapis.com/v1/${root}:commit`, {
    method: 'POST',
    headers: { authorization: request.headers.get('authorization') ?? '', 'content-type': 'application/json' },
    body: JSON.stringify({ writes: [{
      update: { name: `${root}/${path}`, fields: pending },
      ...(current ? { updateMask: { fieldPaths: Object.keys(pending) } } : {}),
      updateTransforms: [{ fieldPath: 'requestedAt', setToServerValue: 'REQUEST_TIME' }],
      currentDocument: current ? { updateTime: match.connection_version } : { exists: false },
    }] }),
  })
  if (result.ok) return response(200, { status: 'INVITATION_SENT' })
  const error = await result.json<{ error?: { status?: string } }>().catch(() => null)
  if (['ALREADY_EXISTS', 'ABORTED', 'FAILED_PRECONDITION'].includes(error?.error?.status ?? '')) {
    return response(409, { status: 'EMAIL_MATCH_CHANGED' })
  }
  if (result.status === 401) return response(401, { status: 'AUTH_INVALID' })
  if (result.status === 403) return response(409, { status: 'EMAIL_MATCH_CHANGED' })
  return response(503, { status: 'EMAIL_INVITE_UNAVAILABLE' })
}

async function deviceDriverLookup(
  env: Env, owner: AuthenticatedUser, now: number, action: 'device-lookup' | 'device-resolve', body: LookupInput,
): Promise<Response> {
  const deviceId = body.deviceId
  if (typeof deviceId !== 'string' || !DEVICE_ID.test(deviceId)) return response(400, { status: 'INVALID_REQUEST' })
  const ownership = await readDocument(env, `deviceOwnership/${deviceId}`)
  if (!ownership || text(ownership.fields, 'ownerUserId') !== owner.uid) {
    return response(403, { status: 'PRIMARY_DRIVER_REQUIRED' })
  }

  if (action === 'device-resolve') {
    const { matchId } = body
    if (typeof matchId !== 'string' || !MATCH_ID.test(matchId)) return response(400, { status: 'INVALID_EMAIL_MATCH' })
    const match = await env.DB.prepare(`SELECT target_uid,email_fingerprint FROM device_driver_email_matches
      WHERE match_id=? AND owner_uid=? AND device_id=? AND expires_at>?`)
      .bind(matchId, owner.uid, deviceId, now).first<DeviceMatch>()
    if (!match) return response(404, { status: 'EMAIL_MATCH_EXPIRED' })
    const account = await firebaseAccount(env, { localId: [match.target_uid] })
    if (!account?.email || account.localId !== match.target_uid || await emailFingerprint(account.email) !== match.email_fingerprint) {
      return response(404, { status: 'EMAIL_MATCH_EXPIRED' })
    }
    const target = await eligibleTarget(env, match.target_uid, 'DRIVER')
    if (!target) return response(404, { status: 'EMAIL_MATCH_EXPIRED' })
    // The existing Android device transaction needs this code to authorize the signed credential.
    return response(200, { status: 'DEVICE_INVITE_TARGET', targetUid: match.target_uid, connectionCode: target.code })
  }

  const email = typeof body.email === 'string' ? body.email.trim().toLowerCase() : ''
  if (!EMAIL.test(email) || email.length > 254) return response(400, { status: 'INVALID_EMAIL' })
  if (!await acceptSearch(env, owner.uid, now)) return response(429, { status: 'EMAIL_SEARCH_LIMIT' })
  const account = await firebaseAccount(env, { email: [email] })
  if (!account?.localId || !account.email || account.email.toLowerCase() !== email || account.localId === owner.uid) {
    return response(404, { status: 'NO_EMAIL_MATCH' })
  }
  const target = await eligibleTarget(env, account.localId, 'DRIVER')
  if (!target) return response(404, { status: 'NO_EMAIL_MATCH' })
  const matchId = crypto.randomUUID()
  await env.DB.batch([
    env.DB.prepare('DELETE FROM device_driver_email_matches WHERE expires_at<=?').bind(now),
    env.DB.prepare('DELETE FROM email_lookup_limits WHERE window_started_at<=?').bind(now - 24 * 60 * 60_000),
    env.DB.prepare(`INSERT INTO device_driver_email_matches
      (match_id,owner_uid,device_id,target_uid,email_fingerprint,expires_at) VALUES(?,?,?,?,?,?)`)
      .bind(matchId, owner.uid, deviceId, account.localId, await emailFingerprint(email), now + MATCH_LIFETIME_MS),
  ])
  return response(200, { status: 'EMAIL_MATCH', displayName: target.name, email: account.email, matchId })
}

export async function trustedContactEmailRequest(
  request: Request, env: Env, user: AuthenticatedUser, action: LookupAction,
): Promise<Response> {
  if (!UID.test(user.uid) || !user.email) return response(403, { status: 'DRIVER_REQUIRED' })
  if (!request.headers.get('content-type')?.toLowerCase().startsWith('application/json') ||
      Number(request.headers.get('content-length')) > 1024) return response(400, { status: 'INVALID_REQUEST' })
  const input = await request.text()
  if (input.length > 1024) return response(400, { status: 'INVALID_REQUEST' })
  let body: LookupInput | null
  try { body = JSON.parse(input) } catch { return response(400, { status: 'INVALID_REQUEST' }) }
  if (!body || typeof body !== 'object' || Array.isArray(body)) return response(400, { status: 'INVALID_REQUEST' })
  try {
    const driver = await readDocument(env, `users/${user.uid}`)
    if (!activeProfile(driver, 'DRIVER')) return response(403, { status: 'DRIVER_REQUIRED' })
    const now = Date.now()
    if (action === 'device-lookup' || action === 'device-resolve') return await deviceDriverLookup(env, user, now, action, body)
    if (action === 'invite') return await sendInvitation(request, env, user, driver, now, body.matchId)
    await env.DB.batch([
      env.DB.prepare('DELETE FROM email_lookup_matches WHERE expires_at<=?').bind(now),
      env.DB.prepare('DELETE FROM email_lookup_limits WHERE window_started_at<=?').bind(now - 24 * 60 * 60_000),
    ])
    return await findContact(env, user, now, body.email)
  } catch {
    return response(503, { status: action === 'lookup' || action === 'device-lookup' ? 'EMAIL_LOOKUP_UNAVAILABLE' : 'EMAIL_INVITE_UNAVAILABLE' })
  }
}
