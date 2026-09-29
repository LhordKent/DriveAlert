import { Link } from 'react-router-dom'
import { Activity, Clock3, FileText, UsersRound } from 'lucide-react'
import { PageHeader } from '../../components/PageHeader'
import { EmptyState, ErrorState, PageSkeleton } from '../../components/PageState'
import { RecordList } from '../../components/RecordList'
import { usePortal } from '../../app/providers/portalContext'
import { formatRelativeTime } from '../../lib/date'

export function DashboardPage() {
  const { approvedDrivers, incomingRequests, outgoingRequests, recordsByDriver, connectionsStatus, connectionsError } = usePortal()
  const allRecords = approvedDrivers.flatMap((driver) => recordsByDriver[driver.driverUserId]?.records ?? []).sort((a, b) => b.periodStartedAt.toMillis() - a.periodStartedAt.toMillis())
  const unreadCount = approvedDrivers.reduce((sum, driver) => sum + (recordsByDriver[driver.driverUserId]?.unreadCount ?? 0), 0)
  if (connectionsStatus === 'loading') return <><PageHeader title="Dashboard" description="Your Trusted Contact overview." /><PageSkeleton rows={4} /></>
  if (connectionsStatus === 'error' && connectionsError) return <><PageHeader title="Dashboard" /><ErrorState error={connectionsError} /></>

  const latestRecord = allRecords[0]
  const summaries = [
    { label: 'Connected Drivers', value: approvedDrivers.length, icon: UsersRound, to: '/drivers' },
    { label: 'Pending requests', value: incomingRequests.length + outgoingRequests.length, icon: Clock3, to: '/requests' },
    { label: 'Shared Stage 3 records', value: allRecords.length, icon: FileText, to: '/drivers' },
    {
      label: 'Latest Shared Activity',
      value: latestRecord ? formatRelativeTime(latestRecord.periodStartedAt) : 'None yet',
      icon: Activity,
      to: latestRecord ? `/drivers/${encodeURIComponent(latestRecord.driverUserId)}/records/${encodeURIComponent(latestRecord.stageSyncRecordId)}` : '/drivers',
    },
  ]

  return <>
    <PageHeader eyebrow="Trusted Contact" title="Dashboard" description="Synchronized Stage 3 awareness from approved Drivers. This is historical activity, not live monitoring." />
    {unreadCount > 0 && <Link to="/drivers" className="mb-5 block rounded-control border border-warning/30 bg-warning-soft p-4 text-sm font-semibold text-warning" role="status">New shared activity: {unreadCount} unread record{unreadCount === 1 ? '' : 's'}. Review connected Drivers.</Link>}
    <section aria-label="Summary" className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
      {summaries.map(({ label, value, icon: Icon, to }) => <Link key={label} to={to} className="surface flex min-h-28 items-center gap-4 p-5 transition-colors hover:bg-strong"><span className="grid size-10 shrink-0 place-items-center rounded-control bg-strong text-secondary"><Icon className="size-5" aria-hidden="true" /></span><span className="min-w-0"><span className="block truncate text-xl font-bold text-primary">{value}</span><span className="mt-1 block text-sm text-secondary">{label}</span></span></Link>)}
    </section>
    <div className="mt-9 grid gap-8 xl:grid-cols-[minmax(0,1.4fr)_minmax(18rem,0.6fr)]">
      <section>
        <div className="mb-3 flex items-center justify-between"><h2 className="section-title">Recent shared activity</h2>{allRecords.length > 0 && <Link to="/drivers" className="text-sm font-semibold text-secondary hover:text-primary">All Drivers</Link>}</div>
        {allRecords.length === 0 ? <EmptyState title="No shared records yet" message="Eligible Stage 3 transition and persistence records will appear after an approved Driver synchronizes them." /> : <div className="surface px-4 sm:px-5"><RecordList driverId={allRecords[0].driverUserId} records={allRecords} limit={6} /></div>}
      </section>
      <aside>
        <h2 className="section-title">Next actions</h2>
        <div className="mt-3 surface divide-y divide-border-soft"><Link to="/requests" className="block min-h-16 p-4 hover:bg-strong"><span className="font-semibold text-primary">Review requests</span><span className="mt-1 block text-sm text-secondary">{incomingRequests.length ? `${incomingRequests.length} waiting for your response` : 'No incoming requests'}</span></Link><Link to="/requests/invite" className="block min-h-16 p-4 hover:bg-strong"><span className="font-semibold text-primary">Invite a Driver</span><span className="mt-1 block text-sm text-secondary">Use their DriveAlert connection code</span></Link></div>
      </aside>
    </div>
    <p className="mt-8 text-xs leading-5 text-muted">DriveAlert provides secondary awareness only. It is not medical confirmation, emergency response, or a substitute for contacting the Driver when appropriate.</p>
  </>
}
