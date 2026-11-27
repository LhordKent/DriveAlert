import { useEffect, useState } from 'react'
import { useAuth } from '../../app/providers/authContext'
import type { TrustedContactConnection } from '../../models/domain'

export interface ConnectionContactDetails {
  email: string | null
  phoneNumber: string | null
}

type DetailRow = ConnectionContactDetails & { connectionId: string }
const emptyDetails: ReadonlyMap<string, ConnectionContactDetails> = new Map()
const workerOrigin = import.meta.env.VITE_NOTIFICATION_WORKER_URL ||
  (import.meta.env.VITE_USE_FIREBASE_EMULATORS === 'true' || import.meta.env.MODE === 'test'
    ? 'http://127.0.0.1:8787'
    : 'https://drivealert-notifications.drivealert-notification-worker.workers.dev')

export function useConnectionContactDetails(connections: TrustedContactConnection[]) {
  const { user } = useAuth()
  const connectionKey = JSON.stringify(connections.map(({ connectionId }) => connectionId).sort())
  const [result, setResult] = useState<{
    uid: string
    connectionKey: string
    details: ReadonlyMap<string, ConnectionContactDetails>
  }>()

  useEffect(() => {
    const ids = JSON.parse(connectionKey) as string[]
    if (!user || !ids.length) {
      setResult(undefined)
      return
    }
    let controller: AbortController | undefined

    const refresh = async () => {
      controller?.abort()
      const request = new AbortController()
      controller = request
      try {
        const token = await user.getIdToken()
        const details = new Map<string, ConnectionContactDetails>()
        for (let start = 0; start < ids.length; start += 20) {
          const batch = ids.slice(start, start + 20)
          const response = await fetch(new URL('/v1/connection-contact-details', workerOrigin), {
            method: 'POST',
            headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
            body: JSON.stringify({ connectionIds: batch }),
            signal: request.signal,
            redirect: 'error',
          })
          if (!response.ok) throw new Error('Contact details are unavailable.')
          const body = await response.json() as { details: DetailRow[] }
          for (const row of body.details) {
            if (batch.includes(row.connectionId)) details.set(row.connectionId, row)
          }
        }
        if (!request.signal.aborted) setResult({ uid: user.uid, connectionKey, details })
      } catch {
        if (!request.signal.aborted) setResult({ uid: user.uid, connectionKey, details: emptyDetails })
      }
    }

    void refresh()
    const refreshOnReturn = () => {
      if (document.visibilityState === 'visible') void refresh()
    }
    document.addEventListener('visibilitychange', refreshOnReturn)
    return () => {
      controller?.abort()
      document.removeEventListener('visibilitychange', refreshOnReturn)
    }
  }, [user, connectionKey])

  return result?.uid === user?.uid && result?.connectionKey === connectionKey ? result.details : emptyDetails
}
