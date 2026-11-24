import type { TrustedContactConnection } from '../models/domain'

export function isIncomingDriverInvitation(connection: TrustedContactConnection, contactUid?: string): boolean {
  return connection.status === 'PENDING' && connection.requestedByUserId === connection.driverUserId
    && connection.trustedContactUserId === contactUid
}

export function signLabel(sign: string): string {
  const labels: Record<string, string> = {
    PROLONGED_EYE_CLOSURE: 'Prolonged Eye Closure',
    YAWNING: 'Yawning',
    HEAD_NODDING: 'Head Nodding',
  }
  return labels[sign] ?? sign.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, (character) => character.toUpperCase())
}
