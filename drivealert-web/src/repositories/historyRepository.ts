import type { StageSyncRecord } from '../models/domain'

export const HISTORY_DAY_MS = 86_400_000
export interface HistoryPolicy { userId: string; days: number | null; expiredBefore: number }
export interface HistoryDismissal { userId: string; driverId: string; recordId: string }
export interface HistorySnapshot { policy: HistoryPolicy; dismissals: HistoryDismissal[] }
const databaseName = 'drivealert-history-v1'

export function validateRetention(days: number | null) {
  if (days !== null && (!Number.isInteger(days) || days < 1 || days > 3650)) throw new Error('Enter 1 to 3,650 whole days.')
}
export function shorterRetention(previous: number | null, next: number | null) {
  return next !== null && (previous === null || next < previous)
}
export function retainRecords(snapshot: HistorySnapshot, driverId: string, records: StageSyncRecord[]) {
  const dismissed = new Set(snapshot.dismissals.filter((item) => item.driverId === driverId).map((item) => item.recordId))
  return records.filter((record) => record.periodStartedAt.toMillis() >= snapshot.policy.expiredBefore && !dismissed.has(record.stageSyncRecordId))
}

/** Browser-only personal visibility. This repository never writes to Firebase. */
export class BrowserHistoryRepository {
  private opening: Promise<IDBDatabase> | null = null
  constructor(private readonly factory: () => IDBFactory = () => indexedDB, private readonly clock = Date.now) {}

  private open(): Promise<IDBDatabase> {
    if (!this.opening) {
      this.opening = new Promise<IDBDatabase>((resolve, reject) => {
        const request = this.factory().open(databaseName, 1)
        request.onupgradeneeded = () => {
          request.result.createObjectStore('policies', { keyPath: 'userId' })
          const dismissals = request.result.createObjectStore('dismissals', { keyPath: ['userId', 'driverId', 'recordId'] })
          dismissals.createIndex('userId', 'userId')
        }
        request.onsuccess = () => {
          const db = request.result
          db.onversionchange = () => { db.close(); this.opening = null }
          resolve(db)
        }
        request.onerror = () => reject(request.error)
        request.onblocked = () => reject(new Error('Close other DriveAlert tabs and retry loading history settings.'))
      }).catch((error) => { this.opening = null; throw error })
    }
    return this.opening
  }

  private async transaction(userId: string, change?: { days: number | null } | { driverId: string; recordIds: string[] }): Promise<HistorySnapshot> {
    if (!userId) throw new Error('Sign in before changing history.')
    const db = await this.open()
    // A single read/write transaction prevents two tabs overwriting removals.
    return new Promise((resolve, reject) => {
      const tx = db.transaction(['policies', 'dismissals'], 'readwrite')
      const policies = tx.objectStore('policies')
      const dismissals = tx.objectStore('dismissals')
      const policyRequest = policies.get(userId)
      const dismissedRequest = dismissals.index('userId').getAll(userId)
      let result: HistorySnapshot
      let failure: unknown
      let completedReads = 0
      const apply = () => {
        if (++completedReads !== 2) return
        try {
          const policy: HistoryPolicy = policyRequest.result ?? { userId, days: null, expiredBefore: Number.MIN_SAFE_INTEGER }
          validateRetention(policy.days)
          if (policy.userId !== userId || !Number.isSafeInteger(policy.expiredBefore)) throw new Error('Stored history settings could not be read.')
          if (change && 'days' in change) policy.days = change.days
          if (policy.days !== null) policy.expiredBefore = Math.max(policy.expiredBefore, this.clock() - policy.days * HISTORY_DAY_MS)
          if (change && 'driverId' in change) {
            for (const recordId of new Set(change.recordIds)) dismissals.put({ userId, driverId: change.driverId, recordId })
          }
          policies.put(policy)
          const existing = dismissedRequest.result as HistoryDismissal[]
          const added = change && 'driverId' in change ? change.recordIds.map((recordId) => ({ userId, driverId: change.driverId, recordId })) : []
          result = { policy, dismissals: [...existing, ...added] }
        } catch (error) { failure = error; tx.abort() }
      }
      policyRequest.onsuccess = apply
      dismissedRequest.onsuccess = apply
      tx.oncomplete = () => resolve(result)
      tx.onabort = tx.onerror = () => reject(failure ?? tx.error ?? new Error('History changes could not be saved in this browser.'))
    })
  }

  load(userId: string) { return this.transaction(userId) }
  save(userId: string, days: number | null) { validateRetention(days); return this.transaction(userId, { days }) }
  dismiss(userId: string, driverId: string, recordIds: string[]) {
    if (!driverId || recordIds.some((id) => !id)) throw new Error('Choose an available history record.')
    return this.transaction(userId, { driverId, recordIds })
  }
}

export const browserHistoryRepository = new BrowserHistoryRepository()
