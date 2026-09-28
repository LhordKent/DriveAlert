import { createContext, useContext } from 'react'
import type { User } from 'firebase/auth'

export interface AuthContextValue { user: User | null; loading: boolean }
export const AuthContext = createContext<AuthContextValue | null>(null)
export function useAuth() {
  const value = useContext(AuthContext)
  if (!value) throw new Error('useAuth must be used within AuthProvider')
  return value
}
