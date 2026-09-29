import { describe, expect, it } from 'vitest'
import { contentFor } from './fcm'
import type { StageRecord } from './types'

const transition: StageRecord = {
  id: 'record-1',
  driverUid: 'driver-1',
  type: 'STAGE_3_TRANSITION',
  periodStartedAtMs: 1_000_000,
  uploadedAtMs: 1_001_000,
}

describe('notification policy', () => {
  it('makes a fresh transition audible and high priority', () => {
    expect(contentFor('Alex', [transition])).toMatchObject({ audible: true, priority: 'HIGH' })
  })

  it('keeps a fresh persistence update silent and normal priority', () => {
    expect(contentFor('Alex', [{ ...transition, type: 'STAGE_3_PERSISTENCE' }])).toMatchObject({ audible: false, priority: 'NORMAL' })
  })

  it('collapses a delayed or multi-record group into one audible summary', () => {
    const result = contentFor('Alex', [
      { ...transition, uploadedAtMs: transition.periodStartedAtMs + 6 * 60_000 },
      { ...transition, id: 'record-2', periodStartedAtMs: transition.periodStartedAtMs + 60_000 },
    ])
    expect(result).toMatchObject({ audible: true, priority: 'HIGH' })
    expect(result.body).toContain('2 Stage 3 updates')
  })
})
