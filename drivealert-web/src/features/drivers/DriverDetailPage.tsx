import { useEffect } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, CalendarDays } from 'lucide-react'
import { usePortal } from '../../app/providers/portalContext'
import { PageHeader } from '../../components/PageHeader'
import { EmptyState, ErrorState, PageSkeleton } from '../../components/PageState'
import { RecordList } from '../../components/RecordList'
import { StatusBadge } from '../../components/StatusBadge'
import { formatDateTime } from '../../lib/date'
import { PortalError } from '../../models/domain'

export function DriverDetailPage() {
  const { driverId = '' } = useParams()
  const { approvedDrivers, recordsByDriver, connectionsStatus, markDriverViewed } = usePortal()
  const driver = approvedDrivers.find((connection) => connection.driverUserId === driverId)
  const recordState = recordsByDriver[driverId]
  useEffect(() => {
    if (driverId && recordState?.status === 'ready') void markDriverViewed(driverId)
  }, [driverId, recordState?.status, recordState?.records.length, markDriverViewed])
  if (connectionsStatus === 'loading') return <PageSkeleton rows={4} />
  if (!driver) return <><PageHeader title="Driver unavailable" /><ErrorState error={new PortalError('permission', 'This Driver is missing, disconnected, or no longer shared with your account.')} /></>
  return <><Link to="/drivers" className="mb-5 inline-flex min-h-12 items-center gap-2 text-sm font-semibold text-secondary hover:text-primary"><ArrowLeft className="size-4" />Back to Drivers</Link><PageHeader title={driver.driverName || 'DriveAlert Driver'} description={driver.driverEmail || 'No Driver email is stored on this relationship.'} action={<StatusBadge label="Approved" tone="success" />} /><section className="mb-8 grid gap-3 sm:grid-cols-3"><div className="surface p-4"><p className="text-xs text-muted">Connected</p><p className="mt-1 text-sm font-semibold text-primary">{formatDateTime(driver.approvedAt)}</p></div><div className="surface p-4"><p className="text-xs text-muted">Shared records</p><p className="mt-1 text-xl font-bold text-primary">{recordState?.records.length ?? 0}</p></div><div className="surface p-4"><p className="text-xs text-muted">Record boundary</p><p className="mt-1 text-sm font-semibold text-primary">Stage 3 only</p></div></section><section><div className="mb-3 flex items-center gap-2"><CalendarDays className="size-5 text-muted" aria-hidden="true" /><h2 className="section-title">Stage 3 synchronization timeline</h2></div>{!recordState || recordState.status === 'loading' ? <PageSkeleton rows={3} /> : recordState.status === 'error' && recordState.error ? <ErrorState error={recordState.error} /> : recordState.records.length === 0 ? <EmptyState title="No shared records" message="Future eligible Stage 3 transition or persistence records will appear here after synchronization." /> : <div className="surface px-4 sm:px-6"><RecordList driverId={driverId} records={recordState.records} /></div>}</section></>
}
