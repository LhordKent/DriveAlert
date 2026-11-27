import { expect, it, vi } from 'vitest'
import { connectionDetailsRequest, CONNECTION_DETAILS_PATH, DEVICE_DRIVER_DETAILS_PATH } from './connectionDetails'
import type { Env } from './types'

const project = 'drivealert-lhordkent'
const environment = { FIREBASE_PROJECT_ID: project } as Env
const root = `projects/${project}/databases/(default)/documents`
const readTime = '2026-11-25T06:00:00.123456Z'
const string = (stringValue: string) => ({ stringValue })
const approvedAt = { timestampValue: '2026-11-24T09:00:00Z' }

function relationship(driver: string, contact: string, status: string) {
  return { readTime, found: {
    name: `${root}/trustedContactConnections/${driver}__${contact}`,
    fields: { driverUserId: string(driver), trustedContactUserId: string(contact), status: string(status), approvedAt },
  } }
}

function request(ids: string[], origin?: string) {
  return new Request('https://worker.example' + CONNECTION_DETAILS_PATH, {
    method: 'POST',
    headers: { 'content-type': 'application/json', ...(origin ? { origin } : {}) },
    body: JSON.stringify({ connectionIds: ids }),
  })
}

it('returns only approved peer contact fields in either direction, including older profile values', async () => {
  const read = vi.fn<NonNullable<Parameters<typeof connectionDetailsRequest>[3]>>(async (paths) => {
    if (paths[0].startsWith('trustedContactConnections/')) return [
      relationship('other', 'elsewhere', 'APPROVED'),
      relationship('viewer', 'removed', 'REVOKED'),
      relationship('viewer', 'pending', 'PENDING'),
      relationship('viewer', 'contact', 'APPROVED'),
      relationship('driver', 'viewer', 'APPROVED'),
    ]
    return [
      { found: { name: root + '/users/driver', fields: { email: string('driver@example.test') } } },
      { found: { name: root + '/users/contact', fields: { email: string('contact@example.test'), phoneNumber: string('09123456789') } } },
    ]
  })
  const response = await connectionDetailsRequest(
    request(['other__elsewhere', 'viewer__removed', 'viewer__pending', 'viewer__contact', 'driver__viewer'], 'https://drivealert.web.app'),
    environment,
    async () => ({ uid: 'viewer' }),
    read,
  )
  expect(response.status).toBe(200)
  expect(response.headers.get('access-control-allow-origin')).toBe('https://drivealert.web.app')
  expect(response.headers.get('cache-control')).toBe('no-store')
  expect(read).toHaveBeenNthCalledWith(2, ['users/contact', 'users/driver'], ['email', 'phoneNumber'], readTime)
  expect(await response.json()).toEqual({ details: [
    { connectionId: 'viewer__contact', email: 'contact@example.test', phoneNumber: '09123456789' },
    { connectionId: 'driver__viewer', email: 'driver@example.test', phoneNumber: null },
  ] })
})

it('rejects missing authentication, invalid IDs, and unapproved browser origins before Firestore reads', async () => {
  const read = vi.fn(async () => [])
  const verify = async () => ({ uid: 'viewer' })
  expect((await connectionDetailsRequest(request(['viewer__contact']), environment, undefined, read)).status).toBe(401)
  expect((await connectionDetailsRequest(request(['users/stranger']), environment, verify, read)).status).toBe(400)
  expect((await connectionDetailsRequest(request(['viewer__contact'], 'https://unrelated.example'), environment, verify, read)).status).toBe(403)
  expect(read).not.toHaveBeenCalled()
})

it('returns only saved numbers for current memberships of the signed-in primary driver', async () => {
  const owned = 'AABBCCDDEEFF'
  const other = '112233445566'
  const document = (path: string, fields: Record<string, string>) => ({ readTime, found: {
    name: `${root}/${path}`, fields: Object.fromEntries(Object.entries(fields).map(([key, text]) => [key, string(text)])),
  } })
  const access = (deviceId: string, peer: string, status: string, ownerUserId = 'viewer', driverUserId = peer) =>
    document(`deviceDriverAccess/${deviceId}__${peer}`, { deviceId, ownerUserId, driverUserId, status })
  const read = vi.fn<NonNullable<Parameters<typeof connectionDetailsRequest>[3]>>(async (paths) => {
    if (paths[0].startsWith('deviceOwnership/')) return [
      access(owned, 'current', 'ACTIVE'),
      access(owned, 'pending', 'PENDING'),
      ...['REMOVED', 'LEFT', 'CANCELLED', 'DECLINED'].map((status) => access(owned, status.toLowerCase(), status)),
      access(owned, 'mismatch', 'ACTIVE', 'viewer', 'different'),
      access(owned, 'wrong-owner', 'ACTIVE', 'someone-else'),
      access(other, 'stranger', 'ACTIVE'),
      document(`deviceOwnership/${owned}`, { deviceId: owned, ownerUserId: 'viewer' }),
      document(`deviceOwnership/${other}`, { deviceId: other, ownerUserId: 'someone-else' }),
    ]
    return [document('users/current', { phoneNumber: ' 09123456789 ', email: 'private@example.test' })]
  })
  const accessIds = [
    ...['current', 'pending', 'removed', 'left', 'cancelled', 'declined', 'mismatch', 'wrong-owner'].map((peer) => `${owned}__${peer}`),
    `${other}__stranger`,
  ]
  const lookup = (ids: string[], uid: string) => connectionDetailsRequest(new Request('https://worker.example' + DEVICE_DRIVER_DETAILS_PATH, {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ accessIds: ids }),
  }), environment, async () => ({ uid }), read)

  const response = await lookup(accessIds, 'viewer')
  expect(response.status).toBe(200)
  expect(response.headers.get('cache-control')).toBe('no-store')
  expect(await response.json()).toEqual({ details: [
    { accessId: `${owned}__current`, phoneNumber: '09123456789' },
    { accessId: `${owned}__pending`, phoneNumber: null },
  ] })
  expect(read).toHaveBeenNthCalledWith(2, ['users/current', 'users/pending'], ['phoneNumber'], readTime)
  read.mockClear()
  expect(await (await lookup(accessIds, 'current')).json()).toEqual({ details: [] })
  expect(read).toHaveBeenCalledTimes(1)
  read.mockClear()
  expect((await lookup(['users/stranger'], 'viewer')).status).toBe(400)
  expect(read).not.toHaveBeenCalled()
})
