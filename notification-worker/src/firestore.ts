import { serviceAccessToken } from './auth'
import type { ApprovedConnection, Env, StageRecord } from './types'

type FirestoreValue = Record<string, unknown>
interface FirestoreDocument { name: string; fields?: Record<string, FirestoreValue> }

function text(value: FirestoreValue | undefined): string {
  return typeof value?.stringValue === 'string' ? value.stringValue : ''
}
function integer(value: FirestoreValue | undefined): number {
  return Number(value?.integerValue ?? 0)
}
function timestamp(value: FirestoreValue | undefined): number {
  const raw = value?.timestampValue
  return typeof raw === 'string' ? Date.parse(raw) : Number.NaN
}

async function googleFetch(env: Env, url: string, init?: RequestInit): Promise<Response> {
  const token = await serviceAccessToken(env)
  return fetch(url, { ...init, headers: { ...init?.headers, authorization: `Bearer ${token}` } })
}

export async function readStageRecord(env: Env, driverUid: string, recordId: string): Promise<StageRecord> {
  const path = `projects/${env.FIREBASE_PROJECT_ID}/databases/(default)/documents/users/${encodeURIComponent(driverUid)}/stageSyncRecords/${encodeURIComponent(recordId)}`
  const response = await googleFetch(env, `https://firestore.googleapis.com/v1/${path}`)
  if (!response.ok) throw new Error(response.status === 404 ? 'RECORD_NOT_FOUND' : 'FIRESTORE_READ_FAILED')
  const document = await response.json<FirestoreDocument>()
  const fields = document.fields ?? {}
  const typeRaw = text(fields.recordType)
  const type = typeRaw.includes('PERSISTENCE') ? 'STAGE_3_PERSISTENCE' : typeRaw.includes('TRANSITION') ? 'STAGE_3_TRANSITION' : null
  const uploadedAtMs = timestamp(fields.uploadedAt)
  const periodStartedAtMs = timestamp(fields.periodStartedAt)
  if (text(fields.driverUserId) !== driverUid || integer(fields.warningStageAtDetection) !== 3 ||
      text(fields.warningStage) !== 'STAGE_3' || !type || !Number.isFinite(uploadedAtMs) || !Number.isFinite(periodStartedAtMs)) {
    throw new Error('RECORD_INVALID')
  }
  return { id: recordId, driverUid, type, uploadedAtMs, periodStartedAtMs }
}

export async function approvedConnections(env: Env, driverUid: string): Promise<ApprovedConnection[]> {
  const response = await googleFetch(env, `https://firestore.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/databases/(default)/documents:runQuery`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ structuredQuery: {
      from: [{ collectionId: 'trustedContactConnections' }],
      where: { fieldFilter: { field: { fieldPath: 'driverUserId' }, op: 'EQUAL', value: { stringValue: driverUid } } },
    } }),
  })
  if (!response.ok) throw new Error('FIRESTORE_RELATIONSHIP_READ_FAILED')
  const rows = await response.json<Array<{ document?: FirestoreDocument }>>()
  return rows.flatMap(({ document }) => {
    const fields = document?.fields ?? {}
    const approvedAtMs = timestamp(fields.approvedAt)
    if (text(fields.status) !== 'APPROVED' || !Number.isFinite(approvedAtMs)) return []
    return [{ trustedUid: text(fields.trustedContactUserId), driverName: text(fields.driverName) || 'Your Driver', approvedAtMs }]
  }).filter((value) => value.trustedUid)
}
