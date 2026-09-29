import { doc, onSnapshot, serverTimestamp, setDoc, type Timestamp, type Unsubscribe } from 'firebase/firestore'
import { firestore } from '../firebase/client'
import { toPortalError } from '../lib/errors'
import type { PortalError } from '../models/domain'

export const trustedDriverStateRepository = {
  observe(
    trustedUid: string,
    driverUid: string,
    onValue: (lastViewedAt: Timestamp | null) => void,
    onError: (error: PortalError) => void,
  ): Unsubscribe {
    return onSnapshot(
      doc(firestore, 'users', trustedUid, 'trustedDriverStates', driverUid),
      (snapshot) => onValue(snapshot.exists() ? (snapshot.data().lastViewedAt ?? null) : null),
      (error) => onError(toPortalError(error)),
    )
  },

  markViewed(trustedUid: string, driverUid: string): Promise<void> {
    return setDoc(doc(firestore, 'users', trustedUid, 'trustedDriverStates', driverUid), {
      driverUserId: driverUid,
      lastViewedAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    }, { merge: true })
  },
}
