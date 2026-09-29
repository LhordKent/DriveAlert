import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import type { Unsubscribe } from 'firebase/firestore'
import { useAuth } from './authContext'
import { profileRepository } from '../../repositories/profileRepository'
import { connectionRepository } from '../../repositories/connectionRepository'
import { stageRecordRepository } from '../../repositories/stageRecordRepository'
import { trustedDriverStateRepository } from '../../repositories/trustedDriverStateRepository'
import { requestDirection } from '../../lib/domain'
import {
  type DriverRecordState,
  type PortalError,
  type TrustedContactConnection,
  type UserProfile,
} from '../../models/domain'
import { PortalContext, type LoadStatus, type PortalContextValue } from './portalContext'

export function PortalProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  const [profile, setProfile] = useState<UserProfile | null>(null)
  const [profileStatus, setProfileStatus] = useState<LoadStatus>('idle')
  const [profileError, setProfileError] = useState<PortalError | null>(null)
  const [profileVersion, setProfileVersion] = useState(0)
  const [connections, setConnections] = useState<TrustedContactConnection[]>([])
  const [connectionsStatus, setConnectionsStatus] = useState<LoadStatus>('idle')
  const [connectionsError, setConnectionsError] = useState<PortalError | null>(null)
  const [recordsByDriver, setRecordsByDriver] = useState<Record<string, DriverRecordState>>({})
  const recordSubscriptions = useRef(new Map<string, { approvedAtMillis: number; unsubscribe: Unsubscribe }>())
  const viewStateSubscriptions = useRef(new Map<string, Unsubscribe>())

  const refreshProfile = useCallback(() => setProfileVersion((value) => value + 1), [])

  useEffect(() => {
    setProfile(null)
    setProfileError(null)
    if (!user) {
      setProfileStatus('idle')
      return
    }
    setProfileStatus('loading')
    return profileRepository.observe(
      user.uid,
      (value) => {
        setProfile(value)
        setProfileStatus('ready')
      },
      (error) => {
        setProfileError(error)
        setProfileStatus('error')
      },
    )
  }, [user, profileVersion])

  useEffect(() => {
    setConnections([])
    setConnectionsError(null)
    if (!user) {
      setConnectionsStatus('idle')
      return
    }
    setConnectionsStatus('loading')
    return connectionRepository.observeForTrustedContact(
      user.uid,
      (value) => {
        setConnections(value)
        setConnectionsStatus('ready')
      },
      (error) => {
        setConnectionsError(error)
        setConnectionsStatus('error')
      },
    )
  }, [user])

  const approvedDrivers = useMemo(() => connections.filter((connection) => connection.status === 'APPROVED'), [connections])

  const withUnread = useCallback((state: DriverRecordState): DriverRecordState => ({
    ...state,
    unreadCount: state.records.filter((record) => {
      const receivedAt = record.uploadedAt
      return receivedAt != null && (state.lastViewedAt == null || receivedAt.toMillis() > state.lastViewedAt.toMillis())
    }).length,
  }), [])

  useEffect(() => {
    const active = new Set(approvedDrivers.map((connection) => connection.driverUserId))
    recordSubscriptions.current.forEach((subscription, driverId) => {
      if (!active.has(driverId)) {
        subscription.unsubscribe()
        recordSubscriptions.current.delete(driverId)
        setRecordsByDriver((current) => {
          const next = { ...current }
          delete next[driverId]
          return next
        })
        viewStateSubscriptions.current.get(driverId)?.()
        viewStateSubscriptions.current.delete(driverId)
      }
    })

    approvedDrivers.forEach((connection) => {
      const driverId = connection.driverUserId
      const approvedAtMillis = connection.approvedAt?.toMillis() ?? Date.now()
      const currentSubscription = recordSubscriptions.current.get(driverId)
      if (currentSubscription?.approvedAtMillis === approvedAtMillis) return
      currentSubscription?.unsubscribe()
      setRecordsByDriver((current) => ({
        ...current,
        [driverId]: { status: 'loading', records: [], error: null, lastViewedAt: current[driverId]?.lastViewedAt ?? null, unreadCount: 0 },
      }))
      const unsubscribe = stageRecordRepository.observeForDriver(
        driverId,
        approvedAtMillis,
        (records) => setRecordsByDriver((current) => {
          const prior = current[driverId]
          return {
            ...current,
            [driverId]: withUnread({ status: 'ready', records, error: null, lastViewedAt: prior?.lastViewedAt ?? null, unreadCount: 0 }),
          }
        }),
        (error) => setRecordsByDriver((current) => ({
          ...current,
          [driverId]: { status: 'error', records: current[driverId]?.records ?? [], error, lastViewedAt: current[driverId]?.lastViewedAt ?? null, unreadCount: current[driverId]?.unreadCount ?? 0 },
        })),
      )
      recordSubscriptions.current.set(driverId, { approvedAtMillis, unsubscribe })
      viewStateSubscriptions.current.get(driverId)?.()
      if (user) {
        viewStateSubscriptions.current.set(driverId, trustedDriverStateRepository.observe(
          user.uid,
          driverId,
          (lastViewedAt) => setRecordsByDriver((current) => {
            const prior = current[driverId]
            if (!prior) return current
            return { ...current, [driverId]: withUnread({ ...prior, lastViewedAt }) }
          }),
          () => undefined,
        ))
      }
    })
  }, [approvedDrivers, user, withUnread])

  useEffect(() => () => {
    recordSubscriptions.current.forEach(({ unsubscribe }) => unsubscribe())
    recordSubscriptions.current.clear()
    viewStateSubscriptions.current.forEach((unsubscribe) => unsubscribe())
    viewStateSubscriptions.current.clear()
  }, [])

  const markDriverViewed = useCallback(async (driverUid: string) => {
    if (!user) return
    await trustedDriverStateRepository.markViewed(user.uid, driverUid)
  }, [user])

  const requests = useMemo(() => connections
    .filter((connection) => connection.status === 'PENDING')
    .map((connection) => ({ connection, direction: requestDirection(connection, user?.uid ?? '') })), [connections, user?.uid])

  const value = useMemo<PortalContextValue>(() => ({
    profile,
    profileStatus,
    profileError,
    connections,
    connectionsStatus,
    connectionsError,
    approvedDrivers,
    incomingRequests: requests.filter((request) => request.direction === 'INCOMING'),
    outgoingRequests: requests.filter((request) => request.direction === 'OUTGOING'),
    recordsByDriver,
    markDriverViewed,
    refreshProfile,
  }), [profile, profileStatus, profileError, connections, connectionsStatus, connectionsError, approvedDrivers, requests, recordsByDriver, markDriverViewed, refreshProfile])

  return <PortalContext.Provider value={value}>{children}</PortalContext.Provider>
}
