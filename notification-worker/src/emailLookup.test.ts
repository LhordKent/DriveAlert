import { readFileSync } from 'node:fs'
import { DatabaseSync } from 'node:sqlite'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { d1 } from '../tests/d1'
import { trustedContactEmailRequest } from './emailLookup'
import type { Env } from './types'

vi.mock('./auth', () => ({ emailLookupAccessToken: async () => 'owner', serviceAccessToken: async () => 'owner' }))

const driver = { uid: 'driver', email: 'driver@example.test' }
const contact = { uid: 'contact', email: 'contact@example.test' }
const connectionCode = 'DA' + '3'.repeat(26)
const migrations = ['0002_trusted_contact_email_lookup', '0003_device_driver_email_lookup']
  .map((name) => readFileSync(new URL(`../migrations/${name}.sql`, import.meta.url), 'utf8')).join('\n')
const field = (stringValue: string) => ({ stringValue })

function emailRequest(env: Env, action: Parameters<typeof trustedContactEmailRequest>[3], body: object, user = driver, token = 'driver-id-token') {
  return trustedContactEmailRequest(new Request('https://worker.test', {
    method: 'POST',
    headers: { authorization: `Bearer ${token}`, 'content-type': 'application/json' },
    body: JSON.stringify(body),
  }), env, user, action)
}

describe('Trusted Contact email lookup', () => {
  let database: DatabaseSync
  let env: Env
  let targetRole: string
  let targetStatus: string
  let relationship: Record<string, unknown> | undefined
  let account: { localId: string; email: string; emailVerified: boolean; disabled?: boolean }
  const commit = vi.fn()

  beforeEach(() => {
    database = new DatabaseSync(':memory:')
    database.exec(migrations)
    env = { DB: d1(database), FIREBASE_PROJECT_ID: 'test', FIREBASE_CLIENT_EMAIL: '', FIREBASE_PRIVATE_KEY: '' }
    targetRole = 'TRUSTED_CONTACT'
    targetStatus = 'ACTIVE'
    relationship = undefined
    account = { localId: contact.uid, email: contact.email, emailVerified: true }
    commit.mockReset()
    commit.mockResolvedValue(Response.json({}))
    vi.stubGlobal('fetch', vi.fn(async (input: string, init?: RequestInit) => {
      if (input.endsWith('/accounts:lookup')) {
        const { email, localId } = JSON.parse(String(init?.body)) as { email?: string[]; localId?: string[] }
        return Response.json({ users: (email?.[0] === account.email || localId?.[0] === account.localId) ? [account] : [] })
      }
      if (input.endsWith('/users/driver') || input.endsWith('/users/another-driver')) {
        return Response.json({ fields: { userRole: field('DRIVER'), firstName: field('Test Driver') } })
      }
      if (input.endsWith('/users/contact')) return Response.json({ fields: {
        accountStatus: field(targetStatus), userRole: field(targetRole), firstName: field('Test'), lastName: field('Contact'), suffix: field('3rd'),
        emailDiscoveryEnabled: { booleanValue: false }, connectionCode: field(connectionCode),
        phoneNumber: field('09123456789'), email: field('unverified-profile-field@example.test'),
      } })
      if (input.endsWith(`/connectionCodes/${connectionCode}`)) {
        return Response.json({ fields: { active: { booleanValue: true }, ownerUserId: field(contact.uid) } })
      }
      if (input.includes('/trustedContactConnections/')) return relationship ? Response.json(relationship) : new Response(null, { status: 404 })
      if (input.endsWith('/deviceOwnership/AABBCCDDEEFF') || input.endsWith('/deviceOwnership/112233445566')) {
        return Response.json({ fields: { ownerUserId: field(driver.uid) } })
      }
      if (input.endsWith('/documents:commit')) return commit(init)
      return new Response(null, { status: 404 })
    }))
  })

  afterEach(() => { vi.unstubAllGlobals(); database.close() })

  async function lookup() {
    const response = await emailRequest(env, 'lookup', { email: ' CONTACT@EXAMPLE.TEST ' })
    expect(response.status).toBe(200)
    return response.json<{ displayName: string; email: string; matchId: string; status: string }>()
  }

  it('returns the verified name and email with a private match ID, and writes only on invite', async () => {
    const match = await lookup()
    expect(match).toEqual({ status: 'EMAIL_MATCH', displayName: 'Test Contact 3rd', email: contact.email, matchId: expect.any(String) })
    expect(commit).not.toHaveBeenCalled()
    const result = await emailRequest(env, 'invite', { matchId: match.matchId })
    expect(result.status).toBe(200)
    const request = commit.mock.calls[0][0] as RequestInit
    expect(new Headers(request.headers).get('authorization')).toBe('Bearer driver-id-token')
    expect(JSON.parse(String(request.body)).writes[0]).toMatchObject({
      update: { fields: { status: field('PENDING'), requestedByUserId: field(driver.uid), trustedContactEmail: field('') } },
      updateTransforms: [{ fieldPath: 'requestedAt', setToServerValue: 'REQUEST_TIME' }],
      currentDocument: { exists: false },
    })
  })

  it('returns no match for a deactivated or unverified contact, a miss, and self', async () => {
    targetStatus = 'DEACTIVATED'
    expect((await emailRequest(env, 'lookup', { email: contact.email })).status).toBe(404)
    targetStatus = 'ACTIVE'
    account.emailVerified = false
    expect((await emailRequest(env, 'lookup', { email: contact.email })).status).toBe(404)
    account = { localId: driver.uid, email: driver.email, emailVerified: true }
    expect((await emailRequest(env, 'lookup', { email: driver.email })).status).toBe(404)
    expect((await emailRequest(env, 'lookup', { email: 'missing@example.test' })).status).toBe(404)
    expect(commit).not.toHaveBeenCalled()
  })

  it('rejects a match when reused by another Driver or after deactivation, email change, or expiry', async () => {
    const { matchId } = await lookup()
    expect((await emailRequest(env, 'invite', { matchId }, { ...driver, uid: 'another-driver' })).status).toBe(404)
    targetStatus = 'DEACTIVATED'
    expect((await emailRequest(env, 'invite', { matchId })).status).toBe(404)
    targetStatus = 'ACTIVE'
    account.email = 'new@example.test'
    expect((await emailRequest(env, 'invite', { matchId })).status).toBe(404)
    account.email = contact.email
    database.prepare('UPDATE email_lookup_matches SET expires_at=0').run()
    expect((await emailRequest(env, 'invite', { matchId })).status).toBe(404)
    expect(commit).not.toHaveBeenCalled()
  })

  it('returns a pending duplicate without writing and requires a fresh match after a change', async () => {
    const { matchId } = await lookup()
    relationship = { updateTime: '2026-01-01T00:00:00Z', fields: { status: field('PENDING'), requestedByUserId: field(driver.uid) } }
    expect(await (await emailRequest(env, 'invite', { matchId })).json()).toEqual({ status: 'INVITATION_EXISTS' })
    relationship = { ...relationship, fields: { status: field('DECLINED'), requestedByUserId: field(driver.uid) } }
    expect(await (await emailRequest(env, 'invite', { matchId })).json()).toEqual({ status: 'EMAIL_MATCH_CHANGED' })
    expect(commit).not.toHaveBeenCalled()
  })

  it('limits repeated searches by the same Driver', async () => {
    for (let i = 0; i < 10; i += 1) expect((await emailRequest(env, 'lookup', { email: contact.email })).status).toBe(200)
    expect((await emailRequest(env, 'lookup', { email: contact.email })).status).toBe(429)
  })

  it('limits device lookup to the primary owner and an eligible Driver, then binds confirmation to the same owner and device', async () => {
    const deviceId = 'AABBCCDDEEFF'
    const query = { deviceId, email: contact.email }
    expect((await emailRequest(env, 'device-lookup', query, { ...driver, uid: 'another-driver' })).status).toBe(403)
    expect((await emailRequest(env, 'device-lookup', query)).status).toBe(404)
    targetRole = 'BOTH'
    const search = await emailRequest(env, 'device-lookup', query)
    const match = await search.json<{ matchId: string }>()
    expect(search.status).toBe(200)
    expect(match).toEqual({ status: 'EMAIL_MATCH', displayName: 'Test Contact 3rd', email: contact.email, matchId: expect.any(String) })
    const confirmation = { deviceId, matchId: match.matchId }
    expect((await emailRequest(env, 'device-resolve', { ...confirmation, deviceId: '112233445566' })).status).toBe(404)
    expect((await emailRequest(env, 'device-resolve', confirmation, { ...driver, uid: 'another-driver' })).status).toBe(403)
    expect((await emailRequest(env, 'invite', { matchId: match.matchId })).status).toBe(404)
    expect(await (await emailRequest(env, 'device-resolve', confirmation)).json()).toEqual({
      status: 'DEVICE_INVITE_TARGET', targetUid: contact.uid, connectionCode,
    })
    account.email = 'changed@example.test'
    expect((await emailRequest(env, 'device-resolve', confirmation)).status).toBe(404)
    expect(commit).not.toHaveBeenCalled()
  })
})
