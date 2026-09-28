import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, CalendarClock, ShieldCheck } from 'lucide-react'
import { usePortal } from '../../app/providers/portalContext'
import { PageHeader } from '../../components/PageHeader'
import { ErrorState, PageSkeleton } from '../../components/PageState'
import { StatusBadge } from '../../components/StatusBadge'
import { formatDateTime } from '../../lib/date'
import { recordTypeLabel, signLabel } from '../../lib/domain'
import { PortalError } from '../../models/domain'

export function RecordDetailPage() {
  const { driverId = '', recordId = '' } = useParams()
  const { approvedDrivers, recordsByDriver, connectionsStatus } = usePortal()
  const driver = approvedDrivers.find((item) => item.driverUserId === driverId)
  const recordState = recordsByDriver[driverId]
  const record = recordState?.records.find((item) => item.stageSyncRecordId === recordId)
  if (!driverId || !recordId) return <ErrorState error={new PortalError('not-found', 'This shared-record URL is malformed.')} />
  if (connectionsStatus === 'loading' || (driver && (!recordState || recordState.status === 'loading'))) return <PageSkeleton rows={5} />
  if (!driver) return <><PageHeader title="Record unavailable" /><ErrorState error={new PortalError('permission', 'The Driver is disconnected, missing, or no longer permits access to this record.')} /></>
  if (recordState.status === 'error' && recordState.error) return <><PageHeader title="Record unavailable" /><ErrorState error={recordState.error} /></>
  if (!record) return <><PageHeader title="Record not found" /><ErrorState error={new PortalError('not-found', 'No eligible shared Stage 3 record matches this URL.')} /></>
  const details = [
    ['Driver', driver.driverName || 'DriveAlert Driver'],
    ['Record type', recordTypeLabel(record.recordType)],
    ['Period start', formatDateTime(record.periodStartedAt)],
    ['Period end', formatDateTime(record.periodEndedAt, 'Not recorded')],
    ['Detected signs', record.signs.map(signLabel).join(', ') || 'No recognized signs'],
    ['Confirmed events in period', String(record.eventCount)],
    ['Session ID', record.sessionId || 'Not available'],
    ['Synchronized', formatDateTime(record.uploadedAt, 'Pending timestamp')],
    ['Synchronization status', record.syncStatus],
  ]
  return <><Link to={`/drivers/${encodeURIComponent(driverId)}`} className="mb-5 inline-flex min-h-12 items-center gap-2 text-sm font-semibold text-secondary hover:text-primary"><ArrowLeft className="size-4" />Back to {driver.driverName || 'Driver'}</Link><PageHeader eyebrow="Shared Stage 3 record" title={recordTypeLabel(record.recordType)} description={formatDateTime(record.periodStartedAt)} action={<StatusBadge label="Stage 3" tone="warning" />} /><div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_22rem]"><section className="surface overflow-hidden"><h2 className="border-b border-border-soft px-5 py-4 font-semibold">Record details</h2><dl className="divide-y divide-border-soft">{details.map(([label, value]) => <div key={label} className="grid gap-1 px-5 py-4 sm:grid-cols-[13rem_1fr] sm:gap-5"><dt className="text-sm text-muted">{label}</dt><dd className={`break-words text-sm text-primary ${label === 'Session ID' ? 'font-mono text-xs' : ''}`}>{value}</dd></div>)}</dl></section><aside className="space-y-4"><div className="surface p-5"><CalendarClock className="size-5 text-warning" /><h2 className="mt-3 font-semibold">Boundary record</h2><p className="mt-2 text-sm leading-6 text-secondary">This record summarizes an eligible Stage 3 transition or continued recurrence period. It is not an individual alert.</p></div><div className="surface p-5"><ShieldCheck className="size-5 text-success" /><h2 className="mt-3 font-semibold">Limited sharing</h2><p className="mt-2 text-sm leading-6 text-secondary">Camera media, location, raw vision measurements, and complete Driver Alert History are not included.</p></div></aside></div></>
}
