export interface Env {
  DB: D1Database
  FIREBASE_PROJECT_ID: string
  FIREBASE_CLIENT_EMAIL: string
  FIREBASE_PRIVATE_KEY: string
}

export interface AuthenticatedUser { uid: string }

export interface DispatchRequest {
  dispatchGroupId: string
  batchId: string
  chunkIndex: number
  chunkCount: number
  recordIds: string[]
}

export interface StageRecord {
  id: string
  driverUid: string
  type: 'STAGE_3_TRANSITION' | 'STAGE_3_PERSISTENCE'
  periodStartedAtMs: number
  uploadedAtMs: number
}

export interface ApprovedConnection {
  trustedUid: string
  driverName: string
  approvedAtMs: number
}
