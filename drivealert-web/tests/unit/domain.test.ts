import { Timestamp } from 'firebase/firestore'
import { describe, expect, it } from 'vitest'
import { deterministicRelationshipId, nextConnectionStatus, recordTypeLabel, requestDirection, signLabel } from '../../src/lib/domain'
import { parseConnection, parseStageSyncRecord, parseUserProfile, trustedRoleAllowed } from '../../src/models/domain'
import { connection } from '../testData'

describe('domain rules', () => {
  it('builds deterministic relationship IDs in Driver then Trusted Contact order', () => expect(deterministicRelationshipId('driver', 'trusted')).toBe('driver__trusted'))
  it.each([
    ['NONE', 'REQUEST', 'PENDING'], ['DECLINED', 'REQUEST', 'PENDING'], ['REVOKED', 'REQUEST', 'PENDING'], ['PENDING', 'ACCEPT', 'APPROVED'], ['PENDING', 'DECLINE', 'DECLINED'], ['PENDING', 'CANCEL', 'REVOKED'], ['APPROVED', 'REVOKE', 'REVOKED'],
  ] as const)('%s + %s becomes %s', (from, action, to) => expect(nextConnectionStatus(from, action)).toBe(to))
  it('rejects invalid transitions', () => expect(() => nextConnectionStatus('APPROVED', 'ACCEPT')).toThrow())
  it('projects both request directions', () => { expect(requestDirection({ ...connection, requestedByUserId: 'trusted-1' }, 'trusted-1')).toBe('OUTGOING'); expect(requestDirection({ ...connection, requestedByUserId: 'driver-1' }, 'trusted-1')).toBe('INCOMING') })
  it('authorizes only Trusted Contact and BOTH roles', () => { expect(trustedRoleAllowed('TRUSTED_CONTACT')).toBe(true); expect(trustedRoleAllowed('BOTH')).toBe(true); expect(trustedRoleAllowed('DRIVER')).toBe(false); expect(trustedRoleAllowed(null)).toBe(false) })
  it('maps visible sign labels and safe unknown labels', () => { expect(signLabel('PROLONGED_EYE_CLOSURE')).toBe('Prolonged Eye Closure'); expect(signLabel('NEW_SIGN')).toBe('New Sign') })
  it('uses clear boundary labels', () => { expect(recordTypeLabel('STAGE_3_TRANSITION')).toBe('Entered Stage 3'); expect(recordTypeLabel('STAGE_3_PERSISTENCE')).toBe('Continued Stage 3 period') })
})

describe('Firestore boundary parsing', () => {
  const timestamp = Timestamp.now()
  it('fills optional profile fields safely', () => expect(parseUserProfile({ firstName: 'Mara', lastName: 'Santos', email: 'mara@example.com', accountStatus: 'ACTIVE' }, 'uid').uid).toBe('uid'))
  it('uses the document ID when a connection payload ID is absent', () => expect(parseConnection({ ...connection, connectionId: undefined }, 'canonical').connectionId).toBe('canonical'))
  it('normalizes legacy persistence values', () => expect(parseStageSyncRecord({ stageSyncRecordId: 'x', driverUserId: 'd', sessionId: 's', recordType: 'STAGE3_PERSISTENCE', periodStartedAt: timestamp, periodEndedAt: null, eventCount: 1, signs: [], warningStage: 'STAGE_3', warningStageAtDetection: 3, syncStatus: 'SYNCED', createdAtClient: timestamp, uploadedAt: timestamp }, 'x').recordType).toBe('STAGE_3_PERSISTENCE'))
  it('rejects malformed external records', () => expect(() => parseStageSyncRecord({}, 'bad')).toThrow())
})
