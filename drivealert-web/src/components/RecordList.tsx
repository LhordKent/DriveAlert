import { CalendarClock } from 'lucide-react'
import { formatDateTime } from '../lib/date'
import { signLabel } from '../lib/domain'
import type { StageSyncRecord } from '../models/domain'

export function RecordList({ records, limit }: { records: StageSyncRecord[]; limit?: number }) {
  const visible = limit ? records.slice(0, limit) : records
  return <ol className="divide-y divide-border-soft" aria-label="Shared alerts">
    {visible.map((record) => (
      <li key={record.stageSyncRecordId} className="grid min-h-20 grid-cols-[auto_minmax(0,1fr)] items-start gap-3 px-1 py-4 sm:gap-4">
        <span className="mt-0.5 grid size-9 place-items-center rounded-full bg-signal-soft text-signal"><CalendarClock className="size-4" aria-hidden="true" /></span>
        <span className="min-w-0">
          <span className="block font-semibold text-primary">{formatDateTime(record.periodStartedAt)}</span>
          <span className="mt-1 block text-sm text-secondary">{record.signs.map(signLabel).join(', ') || 'No recognized signs'}</span>
        </span>
      </li>
    ))}
  </ol>
}
