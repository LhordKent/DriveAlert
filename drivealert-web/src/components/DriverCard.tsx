import { Link } from 'react-router-dom'
import { ChevronRight, Mail, UserRound } from 'lucide-react'
import { formatDateTime, formatRelativeTime } from '../lib/date'
import type { DriverRecordState, TrustedContactConnection } from '../models/domain'
import { StatusBadge } from './StatusBadge'

export function DriverCard({ connection, recordState, onDisconnect }: { connection: TrustedContactConnection; recordState?: DriverRecordState; onDisconnect: () => void }) {
  const latest = recordState?.records[0]
  return (
    <article className="surface p-5 sm:p-6">
      <div className="flex items-start gap-4">
        <span className="grid size-11 shrink-0 place-items-center rounded-full bg-strong text-secondary"><UserRound className="size-5" aria-hidden="true" /></span>
        <div className="min-w-0 flex-1">
          <div className="flex min-w-0 flex-wrap items-center gap-2"><h2 className="min-w-0 max-w-full break-words text-base font-semibold text-primary">{connection.driverName || 'DriveAlert Driver'}</h2><StatusBadge label="Approved" tone="success" />{!!recordState?.unreadCount && <StatusBadge label={`${recordState.unreadCount} new`} tone="warning" />}</div>
          {connection.driverEmail && <p className="mt-1 flex min-w-0 max-w-full items-start gap-1.5 break-all text-sm text-secondary"><Mail className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" /><span className="min-w-0">{connection.driverEmail}</span></p>}
        </div>
      </div>
      <dl className="mt-5 grid gap-4 border-y border-border-soft py-4 sm:grid-cols-2">
        <div><dt className="text-xs font-medium text-muted">Connected</dt><dd className="mt-1 text-sm text-secondary">{formatDateTime(connection.approvedAt)}</dd></div>
        <div><dt className="text-xs font-medium text-muted">Shared activity</dt><dd className="mt-1 text-sm text-secondary">{recordState?.status === 'loading' ? 'Loading records…' : latest ? `${recordState?.records.length ?? 0} records, latest received ${formatRelativeTime(latest.uploadedAt ?? latest.periodStartedAt)}` : 'No shared records yet'}</dd></div>
      </dl>
      <div className="mt-4 flex flex-col gap-2 sm:flex-row">
        <Link to={`/drivers/${encodeURIComponent(connection.driverUserId)}`} className="inline-flex min-h-12 flex-1 items-center justify-center gap-2 rounded-control bg-signal px-4 text-sm font-semibold text-ink hover:bg-signal-pressed">View records<ChevronRight className="size-4" aria-hidden="true" /></Link>
        <button type="button" onClick={onDisconnect} className="inline-flex min-h-12 items-center justify-center rounded-control border border-border px-4 text-sm font-semibold text-secondary hover:bg-strong hover:text-primary">Disconnect</button>
      </div>
    </article>
  )
}
