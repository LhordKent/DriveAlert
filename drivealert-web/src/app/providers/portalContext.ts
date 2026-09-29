import { createContext, useContext } from 'react'
import type { ConnectionRequestView, DriverRecordState, PortalError, TrustedContactConnection, UserProfile } from '../../models/domain'

export type LoadStatus = 'idle' | 'loading' | 'ready' | 'error'
export interface PortalContextValue {
  profile: UserProfile | null
  profileStatus: LoadStatus
  profileError: PortalError | null
  connections: TrustedContactConnection[]
  connectionsStatus: LoadStatus
  connectionsError: PortalError | null
  approvedDrivers: TrustedContactConnection[]
  incomingRequests: ConnectionRequestView[]
  outgoingRequests: ConnectionRequestView[]
  recordsByDriver: Record<string, DriverRecordState>
  markDriverViewed: (driverUid: string) => Promise<void>
  refreshProfile: () => void
}
export const PortalContext = createContext<PortalContextValue | null>(null)
export function usePortal() {
  const value = useContext(PortalContext)
  if (!value) throw new Error('usePortal must be used within PortalProvider')
  return value
}
