import { serviceAccessToken } from './auth'
import type { Env, StageRecord } from './types'

export interface NotificationContent {
  title: string
  body: string
  audible: boolean
  priority: 'HIGH' | 'NORMAL'
}

export function contentFor(driverName: string, records: StageRecord[]): NotificationContent {
  const delayed = records.some((record) => record.uploadedAtMs - record.periodStartedAtMs > 5 * 60_000)
  if (records.length > 1 || delayed) {
    const first = new Date(Math.min(...records.map((record) => record.periodStartedAtMs))).toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit', timeZone: 'UTC' })
    const last = new Date(Math.max(...records.map((record) => record.periodStartedAtMs))).toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit', timeZone: 'UTC' })
    return {
      title: 'Delayed DriveAlert records received',
      body: `${records.length} Stage 3 updates from ${driverName}, recorded ${first}–${last}, were synchronized after connectivity returned.`,
      audible: true,
      priority: 'HIGH',
    }
  }
  if (records[0].type === 'STAGE_3_TRANSITION') return {
    title: 'DriveAlert Stage 3 warning',
    body: `${driverName} reached DriveAlert’s highest warning stage after repeated or persistent warning signs.`,
    audible: true,
    priority: 'HIGH',
  }
  return {
    title: 'Warning activity continued',
    body: `DriveAlert recorded continued qualifying warning activity for ${driverName}.`,
    audible: false,
    priority: 'NORMAL',
  }
}

export async function sendFcm(env: Env, token: string, driverUid: string, recipientUid: string, content: NotificationContent): Promise<'SENT' | 'INVALID' | 'RETRY'> {
  const accessToken = await serviceAccessToken(env)
  const response = await fetch(`https://fcm.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/messages:send`, {
    method: 'POST',
    headers: { authorization: `Bearer ${accessToken}`, 'content-type': 'application/json' },
    body: JSON.stringify({ message: {
      token,
      data: {
        driverUid,
        recipientUid,
        title: content.title,
        body: content.body,
        audible: String(content.audible),
      },
      android: { priority: content.priority },
    } }),
  })
  if (response.ok) return 'SENT'
  const body = await response.text()
  if (response.status === 404 || body.includes('UNREGISTERED') || body.includes('INVALID_ARGUMENT')) return 'INVALID'
  return 'RETRY'
}
