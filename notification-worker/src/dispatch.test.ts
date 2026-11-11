import { readFileSync } from 'node:fs'
import { DatabaseSync } from 'node:sqlite'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Env } from './types'

vi.mock('./auth', () => ({
  verifyFirebaseToken: async (authorization: string | null) => ({ uid: authorization?.slice('Bearer '.length) ?? '' }),
}))
vi.mock('./firestore', () => ({
  readStageRecord: async (_env: Env, driverUid: string, recordId: string) => ({
    id: recordId, driverUid, type: 'STAGE_3_TRANSITION', periodStartedAtMs: 10_000, uploadedAtMs: 11_000,
  }),
  approvedConnections: async () => [{ trustedUid: 'contact', driverName: 'Driver', approvedAtMs: 0 }],
}))
const sendFcm = vi.fn()
vi.mock('./fcm', async (importOriginal) => ({ ...(await importOriginal<typeof import('./fcm')>()), sendFcm: (...args: unknown[]) => sendFcm(...args) }))

const { default: worker } = await import('./index')

/** Minimal D1 adapter over node:sqlite, enough for the worker's prepare/bind/first/all/run/batch calls. */
function d1(db: DatabaseSync): D1Database {
  const statement = (sql: string, args: unknown[] = []) => ({
    bind: (...next: unknown[]) => statement(sql, next),
    first: async () => (db.prepare(sql).get(...args) ?? null),
    all: async () => ({ results: db.prepare(sql).all(...args) }),
    run: async () => db.prepare(sql).run(...args),
  })
  return {
    prepare: (sql: string) => statement(sql),
    batch: async (statements: Array<{ run: () => Promise<unknown> }>) => {
      db.exec('BEGIN')
      try { for (const item of statements) await item.run(); db.exec('COMMIT') } catch (error) { db.exec('ROLLBACK'); throw error }
      return []
    },
  } as unknown as D1Database
}

let db: DatabaseSync
let env: Env
const request = (uid: string, method: string, path: string, body?: unknown) => worker.fetch(new Request(`https://worker.test${path}`, {
  method,
  headers: { authorization: `Bearer ${uid}`, 'content-type': 'application/json' },
  body: body === undefined ? undefined : JSON.stringify(body),
}), env)
const dispatch = () => request('driver', 'POST', '/v1/stage3-notifications',
  { dispatchGroupId: 'group-1', batchId: 'group-1:0', chunkIndex: 0, chunkCount: 1, recordIds: ['record-1'] })

beforeEach(() => {
  db = new DatabaseSync(':memory:')
  db.exec('PRAGMA foreign_keys = ON')
  db.exec(readFileSync(new URL('../migrations/0001_initial.sql', import.meta.url), 'utf8'))
  env = { DB: d1(db), FIREBASE_PROJECT_ID: 'test', FIREBASE_CLIENT_EMAIL: '', FIREBASE_PRIVATE_KEY: '' }
  sendFcm.mockReset()
})

describe('stage 3 dispatch with removed installations', () => {
  it('finishes a group whose pending installation was signed out', async () => {
    await request('contact', 'PUT', '/v1/devices', { installationId: 'install-1', fcmToken: 'token-1' })
    sendFcm.mockResolvedValueOnce('RETRY')
    expect((await dispatch()).status).toBe(202)

    await request('contact', 'DELETE', '/v1/devices/install-1')
    const retry = await dispatch()

    expect(retry.status).toBe(200)
    expect(sendFcm).toHaveBeenCalledTimes(1)
    expect(db.prepare("SELECT status,last_error FROM device_deliveries").get())
      .toEqual({ status: 'TERMINAL', last_error: 'INSTALLATION_REMOVED' })
  })

  it('never sends a pending delivery to another account that re-registered the installation', async () => {
    await request('contact', 'PUT', '/v1/devices', { installationId: 'install-1', fcmToken: 'token-1' })
    sendFcm.mockResolvedValueOnce('RETRY')
    expect((await dispatch()).status).toBe(202)

    await request('someone-else', 'PUT', '/v1/devices', { installationId: 'install-1', fcmToken: 'token-2' })
    const retry = await dispatch()

    expect(retry.status).toBe(200)
    expect(sendFcm).toHaveBeenCalledTimes(1)
  })

  it('still retries and then delivers to an installation that remains registered', async () => {
    await request('contact', 'PUT', '/v1/devices', { installationId: 'install-1', fcmToken: 'token-1' })
    sendFcm.mockResolvedValueOnce('RETRY').mockResolvedValueOnce('SENT')

    expect((await dispatch()).status).toBe(202)
    const retry = await dispatch()

    expect(retry.status).toBe(200)
    expect(await retry.json()).toEqual({ status: 'DELIVERED' })
    expect(sendFcm).toHaveBeenCalledTimes(2)
  })
})
