import type { StageSyncRecord, TrustedContactConnection } from '../models/domain'

export function deterministicRelationshipId(driverUserId: string, trustedContactUserId: string): string {
  if (!driverUserId || !trustedContactUserId) throw new Error('Both participant IDs are required.')
  return `${driverUserId}__${trustedContactUserId}`
}

export function requestDirection(connection: TrustedContactConnection, trustedContactUserId: string) {
  return connection.requestedByUserId === trustedContactUserId ? 'OUTGOING' as const : 'INCOMING' as const
}

export function nextConnectionStatus(
  current: TrustedContactConnection['status'] | 'NONE',
  action: 'REQUEST' | 'ACCEPT' | 'DECLINE' | 'CANCEL' | 'REVOKE',
): TrustedContactConnection['status'] {
  if (action === 'REQUEST' && ['NONE', 'DECLINED', 'REVOKED'].includes(current)) return 'PENDING'
  if (current === 'PENDING' && action === 'ACCEPT') return 'APPROVED'
  if (current === 'PENDING' && action === 'DECLINE') return 'DECLINED'
  if (current === 'PENDING' && action === 'CANCEL') return 'REVOKED'
  if (current === 'APPROVED' && action === 'REVOKE') return 'REVOKED'
  throw new Error(`Invalid connection transition: ${current} + ${action}`)
}

export function signLabel(sign: string): string {
  const labels: Record<string, string> = {
    PROLONGED_EYE_CLOSURE: 'Prolonged Eye Closure',
    YAWNING: 'Yawning',
    HEAD_NODDING: 'Head Nodding',
  }
  return labels[sign] ?? sign.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, (character) => character.toUpperCase())
}

export function recordTypeLabel(type: 'STAGE_3_TRANSITION' | 'STAGE_3_PERSISTENCE'): string {
  return type === 'STAGE_3_TRANSITION' ? 'Entered Stage 3' : 'Continued Stage 3 period'
}

export function isDelayedRecord(record: StageSyncRecord): boolean {
  return record.uploadedAt != null && record.uploadedAt.toMillis() - record.periodStartedAt.toMillis() > 5 * 60 * 1000
}
