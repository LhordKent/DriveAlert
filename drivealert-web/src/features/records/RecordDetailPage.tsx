import { Link, useParams } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import { usePortal } from '../../app/providers/portalContext'
import { HistoryDeleteButton } from '../../components/HistoryDeleteButton'
import { PageHeader } from '../../components/PageHeader'
import { ErrorState, PageSkeleton } from '../../components/PageState'
import { formatDateTime } from '../../lib/date'
import { signLabel } from '../../lib/domain'
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
  if (!record) return <><PageHeader title="Record not found" /><ErrorState error={new PortalError('not-found', 'This record may have been removed from your history or may not be available to your account.')} /></>
  const details = [
    ['Driver', driver.driverName || 'DriveAlert Driver'],
    ['Alert time', formatDateTime(record.periodStartedAt)],
    ['Detected signs', record.signs.map(signLabel).join(', ') || 'No recognized signs'],
  ]
  return (
    <>
      <Link to={`/drivers/${encodeURIComponent(driverId)}`} className="mb-5 inline-flex min-h-12 items-center gap-2 text-sm font-semibold text-secondary hover:text-primary">
        <ArrowLeft className="size-4" aria-hidden="true" />
        Back to Driver
      </Link>
      <PageHeader title="Shared alert" />
      <dl className="surface max-w-2xl divide-y divide-border-soft overflow-hidden">
        {details.map(([label, value]) => (
          <div key={label} className="grid gap-1 px-5 py-4 sm:grid-cols-[10rem_1fr] sm:gap-5">
            <dt className="text-sm text-muted">{label}</dt>
            <dd className="break-words text-sm text-primary">{value}</dd>
          </div>
        ))}
      </dl>
      <div className="mt-5">
        <HistoryDeleteButton driverId={driverId} recordIds={[recordId]} label="Delete record" />
      </div>
    </>
  )
}
