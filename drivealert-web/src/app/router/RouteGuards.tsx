import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { AlertTriangle, RefreshCw, ShieldX } from 'lucide-react'
import { useAuth } from '../providers/authContext'
import { usePortal } from '../providers/portalContext'
import { trustedRoleAllowed } from '../../models/domain'
import { PageSkeleton, ErrorState } from '../../components/PageState'
import { Button } from '../../components/Button'
import { authRepository } from '../../repositories/authRepository'

export function PublicOnlyRoute() {
  const { user, loading } = useAuth()
  if (loading) return <FullPageLoading />
  return user ? <Navigate to="/dashboard" replace /> : <Outlet />
}

export function ProtectedRoute() {
  const { user, loading } = useAuth()
  const { profile, profileStatus, profileError, refreshProfile } = usePortal()
  const location = useLocation()
  if (loading || (user && ['idle', 'loading'].includes(profileStatus))) return <FullPageLoading />
  if (!user) return <Navigate to="/auth/sign-in" replace state={{ from: location.pathname }} />
  if (profileStatus === 'error' && profileError) return <AccessPage icon={AlertTriangle} title="Profile unavailable" message={profileError.message} actions={<Button variant="secondary" onClick={refreshProfile}><RefreshCw className="size-4" />Try again</Button>} />
  if (!profile) return <AccessPage icon={AlertTriangle} title="Profile not found" message="This Firebase account does not have a DriveAlert profile. Create a Trusted Contact account or use the mobile app to finish account setup." />
  if (!trustedRoleAllowed(profile.userRole)) return <AccessPage icon={ShieldX} title="Trusted Contact access required" message="This account is set up only for the Driver role. Signing in here does not change account roles." actions={<Button variant="secondary" onClick={() => authRepository.logout()}>Sign out</Button>} />
  if (profile.accountStatus === 'DEACTIVATED') return <AccessPage icon={ShieldX} title="Account deactivated" message="This DriveAlert profile is not active. Trusted Contact data is unavailable." actions={<Button variant="secondary" onClick={() => authRepository.logout()}>Sign out</Button>} />
  return <Outlet />
}

function FullPageLoading() {
  return <main className="mx-auto flex min-h-screen max-w-xl items-center px-5"><div className="w-full"><div className="mb-6 flex items-center gap-3"><img src="/drivealert-logo.png" alt="DriveAlert" className="size-12" /><span className="text-lg font-bold">DriveAlert</span></div><PageSkeleton rows={2} /></div></main>
}

function AccessPage({ icon: Icon, title, message, actions }: { icon: typeof AlertTriangle; title: string; message: string; actions?: React.ReactNode }) {
  return <main className="grid min-h-screen place-items-center p-5"><div className="surface max-w-lg p-6 sm:p-8"><Icon className="size-8 text-warning" aria-hidden="true" /><h1 className="mt-5 text-xl font-bold">{title}</h1><p className="mt-2 leading-7 text-secondary">{message}</p>{actions && <div className="mt-6">{actions}</div>}</div></main>
}

export function RouteErrorBoundary() {
  return <main className="mx-auto max-w-2xl p-6"><ErrorState error={new (class extends Error { kind = 'not-found' as const })('The requested page was not found.') as never} /></main>
}
