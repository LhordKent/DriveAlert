import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import type { User } from 'firebase/auth'
import { authRepository } from '../../repositories/authRepository'
import { AuthContext } from './authContext'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)
  const [verified, setVerified] = useState(false)
  useEffect(() => {
    let active = true
    let revision = 0
    const unsubscribe = authRepository.observe((nextUser) => {
      const current = ++revision
      setVerified(false)
      setUser(nextUser)
      if (!nextUser?.emailVerified) { setLoading(false); return }
      setLoading(true)
      void authRepository.checkVerification(nextUser).then(() => {
        if (active && current === revision) setVerified(true)
      }).catch(() => {
        // Keep access blocked; the verification screen allows retrying setup.
      }).finally(() => { if (active && current === revision) setLoading(false) })
    })
    return () => { active = false; unsubscribe() }
  }, [])
  const refreshVerification = useCallback(async () => {
    if (!user) return
    await authRepository.checkVerification(user)
    setVerified(true)
  }, [user])
  const value = useMemo(() => ({ user, loading, verified, refreshVerification }), [user, loading, verified, refreshVerification])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
