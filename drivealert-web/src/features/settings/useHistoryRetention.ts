import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { browserHistoryRepository, type HistorySnapshot } from '../../repositories/historyRepository'

export interface HistoryController {
  snapshot: HistorySnapshot | null
  status: 'loading' | 'ready' | 'error'
  error: string | null
  busy: boolean
  refresh: () => Promise<void>
  save: (days: number | null) => Promise<void>
  dismiss: (driverId: string, recordIds: string[]) => Promise<void>
}
const changeEvent = 'drivealert-history-changed'

export function useHistoryRetention(userId: string | undefined): HistoryController {
  const [loaded, setLoaded] = useState<{ owner: string; snapshot: HistorySnapshot } | null>(null)
  const [failure, setFailure] = useState<{ owner: string; message: string } | null>(null)
  const [busy, setBusy] = useState(false)
  const currentUser = useRef(userId)
  const running = useRef(false)
  const channel = useRef<BroadcastChannel | null>(null)
  useEffect(() => { currentUser.current = userId }, [userId])

  const refresh = useCallback(async () => {
    if (!userId) return
    try {
      const snapshot = await browserHistoryRepository.load(userId)
      if (currentUser.current === userId) { setLoaded({ owner: userId, snapshot }); setFailure(null) }
    } catch {
      if (currentUser.current === userId) setFailure({ owner: userId, message: 'History settings could not be loaded. Enable browser storage, then retry.' })
    }
  }, [userId])

  useEffect(() => {
    void refresh()
    const visibleRefresh = () => { if (document.visibilityState === 'visible') void refresh() }
    const timer = window.setInterval(visibleRefresh, 60_000)
    window.addEventListener('focus', visibleRefresh)
    window.addEventListener(changeEvent, visibleRefresh)
    document.addEventListener('visibilitychange', visibleRefresh)
    if (typeof BroadcastChannel !== 'undefined') {
      channel.current = new BroadcastChannel(changeEvent)
      channel.current.onmessage = () => void refresh()
    }
    return () => {
      window.clearInterval(timer)
      window.removeEventListener('focus', visibleRefresh)
      window.removeEventListener(changeEvent, visibleRefresh)
      document.removeEventListener('visibilitychange', visibleRefresh)
      channel.current?.close()
      channel.current = null
    }
  }, [refresh])

  const action = useCallback(async (operation: (uid: string) => Promise<HistorySnapshot>) => {
    if (!userId || running.current) throw new Error('History is not ready. Please retry.')
    running.current = true
    setBusy(true)
    try {
      const snapshot = await operation(userId)
      if (currentUser.current === userId) { setLoaded({ owner: userId, snapshot }); setFailure(null) }
      channel.current?.postMessage('changed')
      window.dispatchEvent(new Event(changeEvent))
    } finally { running.current = false; setBusy(false) }
  }, [userId])

  const snapshot = loaded && loaded.owner === userId ? loaded.snapshot : null
  const error = failure && failure.owner === userId ? failure.message : null
  return useMemo(() => ({ snapshot, status: error ? 'error' : snapshot ? 'ready' : 'loading', error, busy,
    refresh, save: (days) => action((uid) => browserHistoryRepository.save(uid, days)),
    dismiss: (driverId, recordIds) => action((uid) => browserHistoryRepository.dismiss(uid, driverId, recordIds)),
  }), [snapshot, error, busy, refresh, action])
}
