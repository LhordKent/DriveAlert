import {
  collection,
  doc,
  onSnapshot,
  query,
  serverTimestamp,
  updateDoc,
  where,
  type Unsubscribe,
} from 'firebase/firestore'
import { firestore } from '../firebase/client'
import { toPortalError } from '../lib/errors'
import {
  parseConnection,
  type PortalError,
  type TrustedContactConnection,
} from '../models/domain'

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

  async accept(connectionId: string) {
    await this.updateStatus(connectionId, 'APPROVED', 'approvedAt')
  },
  async decline(connectionId: string) {
    await this.updateStatus(connectionId, 'DECLINED', 'declinedAt')
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
