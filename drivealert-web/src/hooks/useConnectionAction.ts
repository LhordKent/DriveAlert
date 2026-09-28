import { useCallback, useState } from 'react'
import { toPortalError } from '../lib/errors'
import type { PortalError } from '../models/domain'

export function useConnectionAction() {
  const [processingIds, setProcessingIds] = useState<Set<string>>(new Set())
  const [actionError, setActionError] = useState<PortalError | null>(null)

  const run = useCallback(async (id: string, action: () => Promise<void>) => {
    setProcessingIds((current) => new Set(current).add(id))
    setActionError(null)
    try {
      await action()
      return true
    } catch (error) {
      setActionError(toPortalError(error))
      return false
    } finally {
      setProcessingIds((current) => {
        const next = new Set(current)
        next.delete(id)
        return next
      })
    }
  }, [])

  return { processingIds, actionError, clearActionError: () => setActionError(null), run }
}
