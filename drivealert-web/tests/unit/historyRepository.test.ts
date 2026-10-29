import { IDBFactory } from 'fake-indexeddb'
import { Timestamp } from 'firebase/firestore'
import { describe, expect, it } from 'vitest'
import { BrowserHistoryRepository, HISTORY_DAY_MS, retainRecords, shorterRetention, validateRetention } from '../../src/repositories/historyRepository'
import { record } from '../testData'

describe('browser history retention', () => {
  it('defaults to manual and validates the same duration range as Android', async () => {
    const repository = new BrowserHistoryRepository(() => new IDBFactory())
    expect((await repository.load('viewer')).policy.days).toBeNull()
    for (const days of [null, 1, 3, 7, 14, 3650]) expect(() => validateRetention(days)).not.toThrow()
    for (const days of [0, -1, 3651, 1.5, NaN, Infinity]) expect(() => validateRetention(days)).toThrow()
    expect(shorterRetention(null, 7)).toBe(true)
    expect(shorterRetention(7, 1)).toBe(true)
    expect(shorterRetention(1, 7)).toBe(false)
    expect(shorterRetention(7, null)).toBe(false)
  })

  it('uses original event time, keeps the exact boundary, and advances cleanup on return', async () => {
    let now = 20 * HISTORY_DAY_MS
    const factory = new IDBFactory()
    const repository = new BrowserHistoryRepository(() => factory, () => now)
    let snapshot = await repository.save('viewer', 3)
    const boundary = now - 3 * HISTORY_DAY_MS
    const records = [-1, 0, 1].map((offset) => ({ ...record, stageSyncRecordId: String(offset), periodStartedAt: Timestamp.fromMillis(boundary + offset), uploadedAt: Timestamp.fromMillis(now) }))
    expect(retainRecords(snapshot, 'driver-1', records).map((item) => item.stageSyncRecordId)).toEqual(['0', '1'])
    now += HISTORY_DAY_MS
    snapshot = await repository.load('viewer')
    expect(retainRecords(snapshot, 'driver-1', records)).toEqual([])
  })

  it('never restores expired or manually removed records after a reload or longer policy', async () => {
    const now = 20 * HISTORY_DAY_MS
    const factory = new IDBFactory()
    const repository = new BrowserHistoryRepository(() => factory, () => now)
    const old = { ...record, periodStartedAt: Timestamp.fromMillis(now - 2 * HISTORY_DAY_MS) }
    const fresh = { ...record, stageSyncRecordId: 'fresh', periodStartedAt: Timestamp.fromMillis(now) }
    await repository.save('viewer', 1)
    await repository.dismiss('viewer', 'driver-1', ['fresh'])
    const restored = new BrowserHistoryRepository(() => factory, () => now)
    await restored.save('viewer', 7)
    await restored.save('viewer', null)
    expect(retainRecords(await restored.load('viewer'), 'driver-1', [old, fresh])).toEqual([])
    expect(retainRecords(await restored.load('another-viewer'), 'driver-1', [old, fresh])).toHaveLength(2)
    expect(retainRecords(await restored.load('viewer'), 'another-driver', [fresh])).toHaveLength(1)
  })

  it('serializes concurrent tabs so no removal is overwritten', async () => {
    const factory = new IDBFactory()
    const firstTab = new BrowserHistoryRepository(() => factory)
    const secondTab = new BrowserHistoryRepository(() => factory)
    await Promise.all([firstTab.dismiss('viewer', 'driver', ['one']), secondTab.dismiss('viewer', 'driver', ['two'])])
    expect((await firstTab.load('viewer')).dismissals.map((item) => item.recordId).sort()).toEqual(['one', 'two'])
  })

  it('reports unavailable browser storage instead of pretending a change was saved', async () => {
    const repository = new BrowserHistoryRepository(() => { throw new Error('Storage unavailable') })
    await expect(repository.load('viewer')).rejects.toThrow('Storage unavailable')
    await expect(repository.save('viewer', 1)).rejects.toThrow('Storage unavailable')
  })
})
