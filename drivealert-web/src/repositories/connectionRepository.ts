import {
  collection,
  doc,
  onSnapshot,
  query,
  runTransaction,
  serverTimestamp,
  updateDoc,
  where,
  type Unsubscribe,
} from 'firebase/firestore'
import { firestore } from '../firebase/client'
import { isValidConnectionCode, normalizeConnectionCode } from '../lib/connectionCode'
import { deterministicRelationshipId } from '../lib/domain'
import { toPortalError } from '../lib/errors'
import {
  connectionCodeMappingSchema,
  fullName,
  parseConnection,
  PortalError,
  type TrustedContactConnection,
  type UserProfile,
} from '../models/domain'

export interface ResolvedConnectionCode {
  userId: string
  displayName: string
  normalizedCode: string
}

export const connectionRepository = {
  observeForTrustedContact(
    trustedContactUserId: string,
    onValue: (connections: TrustedContactConnection[]) => void,
    onError: (error: PortalError) => void,
  ): Unsubscribe {
    const request = query(
      collection(firestore, 'trustedContactConnections'),
      where('trustedContactUserId', '==', trustedContactUserId),
    )
    return onSnapshot(
      request,
      (snapshot) => onValue(snapshot.docs.map((item) => parseConnection(item.data(), item.id)).sort((a, b) => b.requestedAt.toMillis() - a.requestedAt.toMillis())),
      (error) => onError(toPortalError(error)),
    )
  },

  async resolveCode(value: string): Promise<ResolvedConnectionCode> {
    const normalizedCode = normalizeConnectionCode(value)
    if (!isValidConnectionCode(normalizedCode)) throw new PortalError('not-found', 'Enter a complete DriveAlert connection code.')
    try {
      return await runTransaction(firestore, async (transaction) => {
        const snapshot = await transaction.get(doc(firestore, 'connectionCodes', normalizedCode))
        if (!snapshot.exists()) throw new PortalError('not-found', 'No active DriveAlert account was found for this code.')
        const mapping = connectionCodeMappingSchema.parse(snapshot.data())
        if (!mapping.active) throw new PortalError('not-found', 'This DriveAlert connection code is no longer active.')
        return { userId: mapping.ownerUserId, displayName: mapping.displayName || 'DriveAlert user', normalizedCode }
      })
    } catch (error) {
      throw toPortalError(error, 'The connection code could not be checked.')
    }
  },

  async requestDriver(profile: UserProfile, target: ResolvedConnectionCode): Promise<void> {
    if (target.userId === profile.uid) throw new PortalError('conflict', "Use another person's connection code.")
    const connectionId = deterministicRelationshipId(target.userId, profile.uid)
    try {
      await runTransaction(firestore, async (transaction) => {
        const ref = doc(firestore, 'trustedContactConnections', connectionId)
        const existing = await transaction.get(ref)
        if (existing.exists()) {
          const current = parseConnection(existing.data(), existing.id)
          if (current.status === 'PENDING') throw new PortalError('conflict', 'A pending request already exists for this Driver.')
          if (current.status === 'APPROVED') throw new PortalError('conflict', 'This Driver is already connected.')
          transaction.update(ref, {
            requestedByUserId: profile.uid,
            targetConnectionCode: target.normalizedCode,
            status: 'PENDING',
            requestedAt: serverTimestamp(),
            approvedAt: null,
            declinedAt: null,
            revokedAt: null,
          })
          return
        }
        transaction.set(ref, {
          connectionId,
          driverUserId: target.userId,
          trustedContactUserId: profile.uid,
          driverName: target.displayName,
          driverEmail: '',
          trustedContactName: fullName(profile) || 'DriveAlert user',
          trustedContactEmail: profile.email,
          requestedByUserId: profile.uid,
          targetConnectionCode: target.normalizedCode,
          status: 'PENDING',
          requestedAt: serverTimestamp(),
          approvedAt: null,
          declinedAt: null,
          revokedAt: null,
        })
      })
    } catch (error) {
      throw toPortalError(error, 'The connection request could not be sent.')
    }
  },

  async accept(connectionId: string) {
    await this.updateStatus(connectionId, 'APPROVED', 'approvedAt')
  },
  async decline(connectionId: string) {
    await this.updateStatus(connectionId, 'DECLINED', 'declinedAt')
  },
  async cancel(connectionId: string) {
    await this.updateStatus(connectionId, 'REVOKED', 'revokedAt')
  },
  async revoke(connectionId: string) {
    await this.updateStatus(connectionId, 'REVOKED', 'revokedAt')
  },
  async updateStatus(connectionId: string, status: TrustedContactConnection['status'], timestampField: string) {
    try {
      await updateDoc(doc(firestore, 'trustedContactConnections', connectionId), {
        status,
        [timestampField]: serverTimestamp(),
      })
    } catch (error) {
      throw toPortalError(error, 'This request is no longer available.')
    }
  },
}
