import { Timestamp } from 'firebase/firestore'
import type { PortalContextValue } from '../src/app/providers/portalContext'
import type { StageSyncRecord, TrustedContactConnection, UserProfile } from '../src/models/domain'

export const profile: UserProfile = {
  uid: 'trusted-1', firstName: 'Mara', middleName: null, lastName: 'Santos', email: 'mara@example.com', phoneNumber: '+63 900 000 0000', userRole: 'TRUSTED_CONTACT', accountStatus: 'ACTIVE', registeredAt: Timestamp.fromMillis(1000), deactivatedAt: null, connectionCode: 'DA23456789ABCDEFGHJKLMNPQRST',
}

export const connection: TrustedContactConnection = {
  connectionId: 'driver-1__trusted-1', driverUserId: 'driver-1', trustedContactUserId: 'trusted-1', driverName: 'Adrian Cruz', driverEmail: 'adrian@example.com', trustedContactName: 'Mara Santos', trustedContactEmail: 'mara@example.com', requestedByUserId: 'driver-1', targetConnectionCode: profile.connectionCode, status: 'APPROVED', requestedAt: Timestamp.fromMillis(1000), approvedAt: Timestamp.fromMillis(2000), declinedAt: null, revokedAt: null,
}

export const record: StageSyncRecord = {
  stageSyncRecordId: 'record-1', driverUserId: 'driver-1', sessionId: 'session-1', recordType: 'STAGE_3_TRANSITION', periodStartedAt: Timestamp.fromMillis(3000), periodEndedAt: Timestamp.fromMillis(4000), eventCount: 3, signs: ['YAWNING', 'HEAD_NODDING'], warningStage: 'STAGE_3', warningStageAtDetection: 3, syncStatus: 'SYNCED', createdAtClient: Timestamp.fromMillis(4500), uploadedAt: Timestamp.fromMillis(5000),
}

export function portalValue(overrides: Partial<PortalContextValue> = {}): PortalContextValue {
  return {
    profile, profileStatus: 'ready', profileError: null,
    connections: [connection], connectionsStatus: 'ready', connectionsError: null,
    approvedDrivers: [connection], incomingRequests: [], outgoingRequests: [],
    recordsByDriver: { 'driver-1': { status: 'ready', records: [record], error: null, lastViewedAt: null, unreadCount: 1 } },
    markDriverViewed: vi.fn().mockResolvedValue(undefined), refreshProfile: vi.fn(), ...overrides,
  }
}

import { vi } from 'vitest'
