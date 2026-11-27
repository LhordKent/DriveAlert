import { readFileSync } from 'node:fs'
import { DatabaseSync } from 'node:sqlite'
import { afterEach, expect, it, vi } from 'vitest'
import { d1 } from '../tests/d1'
import { trustedContactEmailRequest } from './emailLookup'
import { connectionDetailsRequest, DEVICE_DRIVER_DETAILS_PATH } from './connectionDetails'
import type { Env } from './types'

vi.mock('./auth', () => ({ emailLookupAccessToken: async () => 'owner', serviceAccessToken: async () => 'owner' }))

const authHost = process.env.FIREBASE_AUTH_EMULATOR_HOST ?? ''
const firestoreHost = process.env.FIRESTORE_EMULATOR_HOST ?? ''
const loopback = /^(127\.0\.0\.1|localhost):\d+$/
const useEmulators = loopback.test(authHost) && loopback.test(firestoreHost)
const project = 'drivealert-lhordkent'
const documentRoot = `/v1/projects/${project}/databases/(default)/documents`
const nativeFetch = globalThis.fetch.bind(globalThis)
const string = (stringValue: string) => ({ stringValue })
const boolean = (booleanValue: boolean) => ({ booleanValue })

async function post(url: string, body: object, token?: string): Promise<Response> {
  return nativeFetch(url, {
    method: 'POST',
    headers: { 'content-type': 'application/json', ...(token ? { authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify(body),
  })
}

async function createUser(label: string) {
  const server = `http://${authHost}/identitytoolkit.googleapis.com/v1/accounts:`
  const email = `${label}-${crypto.randomUUID()}@example.test`
  const password = 'Emulator-only-pass-1'
  const registered = await post(`${server}signUp?key=fake-api-key`, { email, password, returnSecureToken: true })
  expect(registered.ok).toBe(true)
  const { localId: uid } = await registered.json<{ localId: string }>()
  const verified = await post(`${server}update`, { localId: uid, emailVerified: true }, 'owner')
  expect(verified.ok).toBe(true)
  const loggedIn = await post(`${server}signInWithPassword?key=fake-api-key`, { email, password, returnSecureToken: true })
  expect(loggedIn.ok).toBe(true)
  const { idToken } = await loggedIn.json<{ idToken: string }>()
  return { uid, email, idToken }
}

async function patch(path: string, fields: Record<string, unknown>, token = 'owner', mask = ''): Promise<Response> {
  return nativeFetch(`http://${firestoreHost}${documentRoot}/${path}${mask}`, {
    method: 'PATCH',
    headers: { authorization: `Bearer ${token}`, 'content-type': 'application/json' },
    body: JSON.stringify({ fields }),
  })
}

afterEach(() => vi.unstubAllGlobals())

it.skipIf(!useEmulators)('shares contact details only after a rule-checked invitation is accepted', async () => {
  const db = new DatabaseSync(':memory:')
  for (const name of ['0002_trusted_contact_email_lookup', '0003_device_driver_email_lookup']) {
    db.exec(readFileSync(new URL(`../migrations/${name}.sql`, import.meta.url), 'utf8'))
  }
  const env: Env = { DB: d1(db), FIREBASE_PROJECT_ID: project, FIREBASE_CLIENT_EMAIL: '', FIREBASE_PRIVATE_KEY: '' }
  try {
    const driver = await createUser('driver')
    const contact = await createUser('contact')
    const code = 'DA' + Array.from(crypto.getRandomValues(new Uint8Array(26)), (n) => '23456789ABCDEFGHJKLMNPQRSTUVWXYZ'[n % 32]).join('')
    const profile = (user: typeof driver, role: string, firstName: string) => ({
      uid: string(user.uid), email: string(user.email), firstName: string(firstName),
      userRole: string(role), accountStatus: string('ACTIVE'),
    })
    const storedNumber = '09123456789'
    expect((await patch(`users/${driver.uid}`, { ...profile(driver, 'DRIVER', 'Test Driver'), phoneNumber: string(storedNumber) })).ok).toBe(true)
    expect((await patch(`users/${contact.uid}`, {
      ...profile(contact, 'BOTH', 'Test Contact'), connectionCode: string(code), emailDiscoveryEnabled: boolean(false),
    })).ok).toBe(true)
    expect((await patch(`connectionCodes/${code}`, {
      ownerUserId: string(contact.uid), displayName: string('Test Contact'), active: boolean(true), createdAt: { timestampValue: new Date().toISOString() },
    })).ok).toBe(true)
    const option = '?updateMask.fieldPaths=emailDiscoveryEnabled'
    expect((await patch(`users/${contact.uid}`, { emailDiscoveryEnabled: boolean(true) }, driver.idToken, option)).status).toBe(403)

    vi.stubGlobal('fetch', (input: string, init?: RequestInit) => {
      const url = new URL(input)
      if (url.hostname === 'identitytoolkit.googleapis.com') return nativeFetch(`http://${authHost}/identitytoolkit.googleapis.com${url.pathname}`, init)
      if (url.hostname === 'firestore.googleapis.com') return nativeFetch(`http://${firestoreHost}${url.pathname}`, init)
      throw new Error('Unexpected test URL')
    })
    function invoke(action: 'lookup' | 'invite', payload: object) {
      return trustedContactEmailRequest(new Request('http://worker.test', {
        method: 'POST',
        headers: { authorization: `Bearer ${driver.idToken}`, 'content-type': 'application/json' },
        body: JSON.stringify(payload),
      }), env, driver, action)
    }
    const connection = `${documentRoot}/trustedContactConnections/${driver.uid}__${contact.uid}`
    const connectionId = `${driver.uid}__${contact.uid}`
    async function detailsAs(uid: string) {
      const response = await connectionDetailsRequest(new Request('http://worker.test/v1/connection-contact-details', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ connectionIds: [connectionId] }),
      }), env, async () => ({ uid }))
      expect(response.status).toBe(200)
      return response.json()
    }
    const readConnection = () => nativeFetch(`http://${firestoreHost}${connection}`, { headers: { authorization: 'Bearer owner' } })
    const lookup = await invoke('lookup', { email: contact.email.toUpperCase() })
    expect(await lookup.clone().json()).toMatchObject({ displayName: 'Test Contact', email: contact.email })
    const { matchId } = await lookup.json<{ matchId: string }>()
    expect((await readConnection()).status).toBe(404)
    const sends = await Promise.all([invoke('invite', { matchId }), invoke('invite', { matchId })])
    expect(sends.map((result) => result.status).sort()).toEqual([200, 409])
    const pending = await (await readConnection()).json()
    expect(pending).toMatchObject({ fields: { status: string('PENDING'), requestedByUserId: string(driver.uid) } })
    expect(await detailsAs(contact.uid)).toEqual({ details: [] })
    const accept = await post(`http://${firestoreHost}${documentRoot}:commit`, { writes: [{
      update: { name: connection.slice('/v1/'.length), fields: { status: string('APPROVED') } },
      updateMask: { fieldPaths: ['status'] },
      updateTransforms: [{ fieldPath: 'approvedAt', setToServerValue: 'REQUEST_TIME' }],
    }] }, contact.idToken)
    expect(accept.ok).toBe(true)
    expect(await (await readConnection()).json()).toMatchObject({ fields: { status: string('APPROVED') } })
    expect(await detailsAs('unrelated-user')).toEqual({ details: [] })
    expect(await detailsAs(contact.uid)).toEqual({ details: [{ connectionId, email: driver.email, phoneNumber: storedNumber }] })
    expect(await detailsAs(driver.uid)).toEqual({ details: [{ connectionId, email: contact.email, phoneNumber: null }] })
    expect((await nativeFetch(`http://${firestoreHost}${documentRoot}/users/${driver.uid}`, {
      headers: { authorization: `Bearer ${contact.idToken}` },
    })).status).toBe(403)
    expect((await patch(`trustedContactConnections/${connectionId}`, { status: string('REVOKED') }, 'owner', '?updateMask.fieldPaths=status')).ok).toBe(true)
    expect(await detailsAs(contact.uid)).toEqual({ details: [] })

    const deviceId = 'AABBCCDDEEFF'
    expect((await patch(`deviceOwnership/${deviceId}`, { ownerUserId: string(driver.uid), deviceId: string(deviceId) })).ok).toBe(true)
    const accessId = `${deviceId}__${contact.uid}`
    expect((await patch(`deviceDriverAccess/${accessId}`, {
      deviceId: string(deviceId), ownerUserId: string(driver.uid), driverUserId: string(contact.uid), status: string('ACTIVE'),
    })).ok).toBe(true)
    expect((await patch(`users/${contact.uid}`, { phoneNumber: string(storedNumber) }, 'owner', '?updateMask.fieldPaths=phoneNumber')).ok).toBe(true)
    async function numbersAs(uid: string) {
      const response = await connectionDetailsRequest(new Request('http://worker.test' + DEVICE_DRIVER_DETAILS_PATH, {
        method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ accessIds: [accessId] }),
      }), env, async () => ({ uid }))
      expect(response.status).toBe(200)
      return response.json()
    }
    expect(await numbersAs(driver.uid)).toEqual({ details: [{ accessId, phoneNumber: storedNumber }] })
    expect(await numbersAs(contact.uid)).toEqual({ details: [] })
    expect((await patch(`deviceDriverAccess/${accessId}`, { status: string('REMOVED') }, 'owner', '?updateMask.fieldPaths=status')).ok).toBe(true)
    expect(await numbersAs(driver.uid)).toEqual({ details: [] })
    const deviceRequest = (action: 'device-lookup' | 'device-resolve', body: object, user = driver) =>
      trustedContactEmailRequest(new Request('http://worker.test', {
        method: 'POST',
        headers: { authorization: `Bearer ${user.idToken}`, 'content-type': 'application/json' },
        body: JSON.stringify({ deviceId, ...body }),
      }), env, user, action)
    expect((await deviceRequest('device-lookup', { email: driver.email }, contact)).status).toBe(403)
    const deviceSearch = await deviceRequest('device-lookup', { email: contact.email })
    const deviceMatch = await deviceSearch.json<{ matchId: string }>()
    expect(deviceSearch.status).toBe(200)
    expect(deviceMatch).toEqual({
      status: 'EMAIL_MATCH', displayName: 'Test Contact', email: contact.email, matchId: expect.any(String),
    })
    expect(await (await deviceRequest('device-resolve', { matchId: deviceMatch.matchId })).json()).toEqual({
      status: 'DEVICE_INVITE_TARGET', targetUid: contact.uid, connectionCode: code,
    })
  } finally {
    db.close()
  }
}, 20_000)
