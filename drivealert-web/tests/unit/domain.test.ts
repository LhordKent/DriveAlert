import { Timestamp } from 'firebase/firestore'
import { describe, expect, it } from 'vitest'
import { isIncomingDriverInvitation, signLabel } from '../../src/lib/domain'
import { parseConnection, parseStageSyncRecord, parseUserProfile, trustedRoleAllowed } from '../../src/models/domain'
import { connection } from '../testData'

describe('domain rules', () => {
  it('shows only pending Driver invitations addressed to the signed-in Trusted Contact', () => {
    const invitation = { ...connection, status: 'PENDING' as const }
    expect(isIncomingDriverInvitation(invitation, 'trusted-1')).toBe(true)
    expect(isIncomingDriverInvitation({ ...invitation, requestedByUserId: 'trusted-1' }, 'trusted-1')).toBe(false)
    expect(isIncomingDriverInvitation(invitation, 'someone-else')).toBe(false)
    expect(isIncomingDriverInvitation(connection, 'trusted-1')).toBe(false)
  })
  it('authorizes only Trusted Contact and BOTH roles', () => { expect(trustedRoleAllowed('TRUSTED_CONTACT')).toBe(true); expect(trustedRoleAllowed('BOTH')).toBe(true); expect(trustedRoleAllowed('DRIVER')).toBe(false); expect(trustedRoleAllowed(null)).toBe(false) })
  it('maps visible sign labels and safe unknown labels', () => { expect(signLabel('PROLONGED_EYE_CLOSURE')).toBe('Prolonged Eye Closure'); expect(signLabel('NEW_SIGN')).toBe('New Sign') })
})

describe('Firestore boundary parsing', () => {
  const timestamp = Timestamp.now()
  it('fills optional profile fields safely', () => expect(parseUserProfile({ firstName: 'Mara', lastName: 'Santos', email: 'mara@example.com', accountStatus: 'ACTIVE' }, 'uid').uid).toBe('uid'))
  it('uses the document ID when a connection payload ID is absent', () => expect(parseConnection({ ...connection, connectionId: undefined }, 'canonical').connectionId).toBe('canonical'))
  it('normalizes legacy persistence values', () => expect(parseStageSyncRecord({ stageSyncRecordId: 'x', driverUserId: 'd', sessionId: 's', recordType: 'STAGE3_PERSISTENCE', periodStartedAt: timestamp, periodEndedAt: null, eventCount: 1, signs: [], warningStage: 'STAGE_3', warningStageAtDetection: 3, syncStatus: 'SYNCED', createdAtClient: timestamp, uploadedAt: timestamp }, 'x').recordType).toBe('STAGE_3_PERSISTENCE'))
  it('rejects malformed external records', () => expect(() => parseStageSyncRecord({}, 'bad')).toThrow())
})
