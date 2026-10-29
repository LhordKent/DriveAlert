import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useHistoryRetention } from '../../src/features/settings/useHistoryRetention'
import { browserHistoryRepository, type HistorySnapshot } from '../../src/repositories/historyRepository'

afterEach(() => vi.restoreAllMocks())
const snapshot = (uid: string): HistorySnapshot => ({ policy: { userId: uid, days: null, expiredBefore: Number.MIN_SAFE_INTEGER }, dismissals: [] })

describe('account-scoped browser history', () => {
  it('hides the previous account snapshot immediately when switching users', async () => {
    let resolveSecond!: (value: HistorySnapshot) => void
    vi.spyOn(browserHistoryRepository, 'load').mockImplementation((uid) => uid === 'first' ? Promise.resolve(snapshot(uid)) : new Promise((resolve) => { resolveSecond = resolve }))
    const { result, rerender } = renderHook(({ uid }) => useHistoryRetention(uid), { initialProps: { uid: 'first' } })
    await waitFor(() => expect(result.current.status).toBe('ready'))
    rerender({ uid: 'second' })
    expect(result.current.snapshot).toBeNull()
    expect(result.current.status).toBe('loading')
    await act(async () => { resolveSecond(snapshot('second')) })
    expect(result.current.snapshot?.policy.userId).toBe('second')
  })

  it('reports storage failure after refresh instead of exposing history with unknown removals', async () => {
    const load = vi.spyOn(browserHistoryRepository, 'load').mockResolvedValue(snapshot('viewer'))
    const { result } = renderHook(() => useHistoryRetention('viewer'))
    await waitFor(() => expect(result.current.status).toBe('ready'))
    load.mockRejectedValue(new Error('storage blocked'))
    await act(async () => result.current.refresh())
    expect(result.current.status).toBe('error')
    expect(result.current.error).toMatch(/Enable browser storage/)
  })
})
