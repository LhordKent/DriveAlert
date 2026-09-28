import { Link } from 'react-router-dom'
import { CalendarClock, ChevronRight } from 'lucide-react'
import { formatDateTime } from '../lib/date'
import { recordTypeLabel, signLabel } from '../lib/domain'
import type { StageSyncRecord } from '../models/domain'
import { StatusBadge } from './StatusBadge'

export function RecordList({ driverId, records, limit }: { driverId?: string; records: StageSyncRecord[]; limit?: number }) {
  const visible = limit ? records.slice(0, limit) : records
  return <ol className="divide-y divide-border-soft" aria-label="Stage 3 synchronization records">
    {visible.map((record) => <li key={record.stageSyncRecordId}>
      <Link to={`/drivers/${encodeURIComponent(record.driverUserId || driverId || '')}/records/${encodeURIComponent(record.stageSyncRecordId)}`} className="group grid min-h-20 grid-cols-[auto_1fr_auto] items-start gap-3 px-1 py-4 sm:gap-4">
        <span className="mt-0.5 grid size-9 place-items-center rounded-full bg-signal-soft text-signal"><CalendarClock className="size-4" aria-hidden="true" /></span>
        <span className="min-w-0"><span className="flex flex-wrap items-center gap-2"><span className="font-semibold text-primary">{recordTypeLabel(record.recordType)}</span><StatusBadge label="Stage 3" tone="warning" /></span><span className="mt-1 block text-sm text-secondary">{formatDateTime(record.periodStartedAt)}</span><span className="mt-1 block truncate text-xs text-muted">{record.signs.map(signLabel).join(', ') || 'No recognized signs'}</span></span>
        <ChevronRight className="mt-2 size-5 text-muted transition-transform duration-200 group-hover:translate-x-0.5 group-hover:text-primary" aria-hidden="true" />
      </Link>
    </li>)}
  </ol>
}
