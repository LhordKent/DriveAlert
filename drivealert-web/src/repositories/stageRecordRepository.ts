import { collection, onSnapshot, orderBy, query, Timestamp, where, type Unsubscribe } from 'firebase/firestore'
import { firestore } from '../firebase/client'
import { toPortalError } from '../lib/errors'
import { parseStageSyncRecord, type PortalError, type StageSyncRecord } from '../models/domain'

export const stageRecordRepository = {
  observeForDriver(
    driverUserId: string,
    approvedAtMillis: number,
    onValue: (records: StageSyncRecord[]) => void,
    onError: (error: PortalError) => void,
  ): Unsubscribe {
    const request = query(
      collection(firestore, 'users', driverUserId, 'stageSyncRecords'),
      where('driverUserId', '==', driverUserId),
      where('periodStartedAt', '>=', Timestamp.fromMillis(approvedAtMillis)),
      orderBy('periodStartedAt', 'desc'),
    )
    return onSnapshot(
      request,
      (snapshot) => onValue(snapshot.docs.map((item) => parseStageSyncRecord(item.data(), item.id))),
      (error) => onError(toPortalError(error)),
    )
  },
}
