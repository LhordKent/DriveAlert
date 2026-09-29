import { verifyFirebaseToken } from './auth'
import { approvedConnections, readStageRecord } from './firestore'
import { contentFor, sendFcm } from './fcm'
import type { DispatchRequest, Env, StageRecord } from './types'

const ID_PATTERN = /^[A-Za-z0-9:_-]{1,160}$/
const MAX_CHUNKS = 25
const MAX_RECORDS_PER_CHUNK = 20
const COMPLETED_RETENTION_MS = 30 * 24 * 60 * 60 * 1000

function json(status: number, value: Record<string, unknown>): Response {
  return Response.json(value, { status, headers: { 'cache-control': 'no-store' } })
}

function errorCode(error: unknown): string {
  return error instanceof Error ? error.message : 'INTERNAL_ERROR'
}

async function parseBody<T>(request: Request): Promise<T> {
  if (!request.headers.get('content-type')?.toLowerCase().includes('application/json')) throw new Error('CONTENT_TYPE_REQUIRED')
  return request.json<T>()
}

function validateDispatch(value: DispatchRequest): void {
  if (!ID_PATTERN.test(value.dispatchGroupId) || !ID_PATTERN.test(value.batchId) ||
      !Number.isInteger(value.chunkIndex) || !Number.isInteger(value.chunkCount) ||
      value.chunkCount < 1 || value.chunkCount > MAX_CHUNKS || value.chunkIndex < 0 || value.chunkIndex >= value.chunkCount ||
      !Array.isArray(value.recordIds) || value.recordIds.length < 1 || value.recordIds.length > MAX_RECORDS_PER_CHUNK ||
      value.recordIds.some((id) => !ID_PATTERN.test(id)) || new Set(value.recordIds).size !== value.recordIds.length) {
    throw new Error('INVALID_REQUEST')
  }
}

async function registerDevice(request: Request, env: Env, uid: string): Promise<Response> {
  const body = await parseBody<{ installationId?: string; fcmToken?: string }>(request)
  if (!body.installationId || !ID_PATTERN.test(body.installationId) || !body.fcmToken || body.fcmToken.length > 4096) {
    return json(400, { status: 'INVALID_REQUEST' })
  }
  const now = Date.now()
  await env.DB.batch([
    env.DB.prepare('DELETE FROM installations WHERE fcm_token = ? AND installation_id <> ?').bind(body.fcmToken, body.installationId),
    env.DB.prepare(`INSERT INTO installations(installation_id,user_id,fcm_token,updated_at) VALUES(?,?,?,?)
      ON CONFLICT(installation_id) DO UPDATE SET user_id=excluded.user_id,fcm_token=excluded.fcm_token,updated_at=excluded.updated_at`)
      .bind(body.installationId, uid, body.fcmToken, now),
    env.DB.prepare(`DELETE FROM installations WHERE user_id = ? AND installation_id NOT IN
      (SELECT installation_id FROM installations WHERE user_id = ? ORDER BY updated_at DESC LIMIT 5)`).bind(uid, uid),
  ])
  return json(200, { status: 'REGISTERED' })
}

async function deleteDevice(request: Request, env: Env, uid: string): Promise<Response> {
  const installationId = decodeURIComponent(new URL(request.url).pathname.split('/').pop() ?? '')
  if (!ID_PATTERN.test(installationId)) return json(400, { status: 'INVALID_REQUEST' })
  await env.DB.prepare('DELETE FROM installations WHERE installation_id = ? AND user_id = ?').bind(installationId, uid).run()
  return json(200, { status: 'DELETED' })
}

async function storeChunk(env: Env, uid: string, body: DispatchRequest): Promise<Response | null> {
  const group = await env.DB.prepare('SELECT driver_uid,chunk_count,status FROM dispatch_groups WHERE group_id = ?')
    .bind(body.dispatchGroupId).first<{ driver_uid: string; chunk_count: number; status: string }>()
  if (group && (group.driver_uid !== uid || group.chunk_count !== body.chunkCount)) return json(409, { status: 'GROUP_CONFLICT' })
  const existing = await env.DB.prepare('SELECT batch_id,record_ids_json FROM dispatch_chunks WHERE group_id = ? AND chunk_index = ?')
    .bind(body.dispatchGroupId, body.chunkIndex).first<{ batch_id: string; record_ids_json: string }>()
  const recordJson = JSON.stringify(body.recordIds)
  if (existing) {
    if (existing.batch_id !== body.batchId || existing.record_ids_json !== recordJson) return json(409, { status: 'CHUNK_CONFLICT' })
    return null
  }
  const validated = await Promise.all(body.recordIds.map((recordId) => readStageRecord(env, uid, recordId)))
  const duplicatePlaceholders = body.recordIds.map(() => '?').join(',')
  const duplicate = await env.DB.prepare(`SELECT record_id FROM dispatch_records WHERE group_id = ? AND record_id IN (${duplicatePlaceholders}) LIMIT 1`)
    .bind(body.dispatchGroupId, ...body.recordIds).first()
  if (duplicate) return json(409, { status: 'DUPLICATE_RECORD' })
  const statements = [
    env.DB.prepare(`INSERT OR IGNORE INTO dispatch_groups(group_id,driver_uid,chunk_count,status,created_at)
      VALUES(?,?,?,'RECEIVING',?)`).bind(body.dispatchGroupId, uid, body.chunkCount, Date.now()),
    env.DB.prepare('INSERT INTO dispatch_chunks(group_id,chunk_index,batch_id,record_ids_json) VALUES(?,?,?,?)')
      .bind(body.dispatchGroupId, body.chunkIndex, body.batchId, recordJson),
    ...validated.map((record) => env.DB.prepare(`INSERT INTO dispatch_records
      (group_id,record_id,record_type,period_started_at,uploaded_at) VALUES(?,?,?,?,?)`)
      .bind(body.dispatchGroupId, record.id, record.type, record.periodStartedAtMs, record.uploadedAtMs)),
  ]
  await env.DB.batch(statements)
  return null
}

async function finalizeGroup(env: Env, uid: string, body: DispatchRequest): Promise<Response> {
  const chunkCount = await env.DB.prepare('SELECT COUNT(*) AS count FROM dispatch_chunks WHERE group_id = ?')
    .bind(body.dispatchGroupId).first<{ count: number }>()
  if ((chunkCount?.count ?? 0) < body.chunkCount) return json(202, { status: 'AWAITING_CHUNKS' })

  const rows = await env.DB.prepare(`SELECT record_id,record_type,period_started_at,uploaded_at
      FROM dispatch_records WHERE group_id = ? ORDER BY period_started_at ASC`)
    .bind(body.dispatchGroupId).all<{ record_id: string; record_type: StageRecord['type']; period_started_at: number; uploaded_at: number }>()
  const records: StageRecord[] = rows.results.map((row) => ({
    id: row.record_id,
    driverUid: uid,
    type: row.record_type,
    periodStartedAtMs: row.period_started_at,
    uploadedAtMs: row.uploaded_at,
  }))
  if (records.length === 0) return json(400, { status: 'EMPTY_GROUP' })

  const connections = await approvedConnections(env, uid)
  for (const connection of connections) {
    if (!records.some((record) => connection.approvedAtMs <= record.periodStartedAtMs)) continue
    const devices = await env.DB.prepare('SELECT installation_id FROM installations WHERE user_id = ? ORDER BY updated_at DESC')
      .bind(connection.trustedUid).all<{ installation_id: string }>()
    if (devices.results.length) await env.DB.batch(devices.results.map((device) => env.DB.prepare(`INSERT OR IGNORE INTO device_deliveries
      (group_id,trusted_uid,installation_id,status,attempts,updated_at) VALUES(?,?,?,'PENDING',0,?)`)
      .bind(body.dispatchGroupId, connection.trustedUid, device.installation_id, Date.now())))
  }

  const deliveries = await env.DB.prepare(`SELECT d.trusted_uid,d.installation_id,i.fcm_token
      FROM device_deliveries d JOIN installations i ON i.installation_id=d.installation_id
      WHERE d.group_id=? AND d.status='PENDING' LIMIT 20`)
    .bind(body.dispatchGroupId).all<{ trusted_uid: string; installation_id: string; fcm_token: string }>()
  for (const delivery of deliveries.results) {
    const connection = connections.find((candidate) => candidate.trustedUid === delivery.trusted_uid)
    if (!connection) {
      await env.DB.prepare(`UPDATE device_deliveries SET status='TERMINAL',last_error='NO_APPROVED_CONTACT',updated_at=?
        WHERE group_id=? AND trusted_uid=? AND installation_id=?`)
        .bind(Date.now(), body.dispatchGroupId, delivery.trusted_uid, delivery.installation_id).run()
      continue
    }
    const eligibleRecords = records.filter((record) => connection.approvedAtMs <= record.periodStartedAtMs)
    const result = await sendFcm(env, delivery.fcm_token, uid, delivery.trusted_uid, contentFor(connection.driverName, eligibleRecords))
    if (result === 'INVALID') {
      await env.DB.batch([
        env.DB.prepare('DELETE FROM installations WHERE installation_id=?').bind(delivery.installation_id),
        env.DB.prepare(`UPDATE device_deliveries SET status='TERMINAL',attempts=attempts+1,last_error='INVALID_TOKEN',updated_at=?
          WHERE group_id=? AND trusted_uid=? AND installation_id=?`)
          .bind(Date.now(), body.dispatchGroupId, delivery.trusted_uid, delivery.installation_id),
      ])
    } else {
      await env.DB.prepare(`UPDATE device_deliveries SET status=?,attempts=attempts+1,last_error=?,updated_at=?
        WHERE group_id=? AND trusted_uid=? AND installation_id=?`)
        .bind(result === 'SENT' ? 'SENT' : 'PENDING', result === 'SENT' ? null : 'FCM_RETRY', Date.now(),
          body.dispatchGroupId, delivery.trusted_uid, delivery.installation_id).run()
    }
  }

  const pending = await env.DB.prepare("SELECT COUNT(*) AS count FROM device_deliveries WHERE group_id=? AND status='PENDING'")
    .bind(body.dispatchGroupId).first<{ count: number }>()
  const total = await env.DB.prepare('SELECT COUNT(*) AS count FROM device_deliveries WHERE group_id=?')
    .bind(body.dispatchGroupId).first<{ count: number }>()
  if ((pending?.count ?? 0) > 0) return json(202, { status: 'DELIVERY_RETRY' })
  await env.DB.prepare("UPDATE dispatch_groups SET status=?,completed_at=? WHERE group_id=?")
    .bind((total?.count ?? 0) === 0 ? 'NO_RECIPIENTS' : 'DELIVERED', Date.now(), body.dispatchGroupId).run()
  return json(200, { status: (total?.count ?? 0) === 0 ? 'NO_RECIPIENTS' : 'DELIVERED' })
}

async function dispatchStage3(request: Request, env: Env, uid: string): Promise<Response> {
  const body = await parseBody<DispatchRequest>(request)
  validateDispatch(body)
  const conflict = await storeChunk(env, uid, body)
  if (conflict) return conflict
  return finalizeGroup(env, uid, body)
}

async function route(request: Request, env: Env): Promise<Response> {
  const url = new URL(request.url)
  const user = await verifyFirebaseToken(request.headers.get('authorization'), env)
  if (request.method === 'PUT' && url.pathname === '/v1/devices') return registerDevice(request, env, user.uid)
  if (request.method === 'DELETE' && url.pathname.startsWith('/v1/devices/')) return deleteDevice(request, env, user.uid)
  if (request.method === 'POST' && url.pathname === '/v1/stage3-notifications') return dispatchStage3(request, env, user.uid)
  return json(404, { status: 'NOT_FOUND' })
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    try {
      await env.DB.prepare("DELETE FROM dispatch_groups WHERE completed_at IS NOT NULL AND completed_at < ?")
        .bind(Date.now() - COMPLETED_RETENTION_MS).run()
      return await route(request, env)
    } catch (error) {
      const code = errorCode(error)
      if (code.startsWith('AUTH_')) return json(401, { status: code })
      if (['INVALID_REQUEST', 'CONTENT_TYPE_REQUIRED', 'RECORD_INVALID', 'RECORD_NOT_FOUND'].includes(code)) {
        return json(400, { status: code })
      }
      return json(503, { status: code })
    }
  },
} satisfies ExportedHandler<Env>
