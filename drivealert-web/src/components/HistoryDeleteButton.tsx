import { useState } from 'react'
import { Button } from './Button'
import { ConfirmDialog } from './ConfirmDialog'
import { usePortal } from '../app/providers/portalContext'

export function HistoryDeleteButton({ driverId, recordIds, label }: { driverId: string; recordIds: string[]; label: 'Delete record' | 'Clear history' }) {
  const { history } = usePortal()
  const [confirmingFor, setConfirmingFor] = useState<string | null>(null)
  const open = confirmingFor !== null && confirmingFor === history.snapshot?.policy.userId && history.status === 'ready'
  const [error, setError] = useState<string | null>(null)
  const confirm = async () => {
    try { await history.dismiss(driverId, recordIds); setConfirmingFor(null); setError(null) }
    catch { setError('History could not be removed. Enable browser storage, then try again.') }
  }
  return <>
    <Button variant="danger" disabled={!recordIds.length || history.status !== 'ready' || history.busy} onClick={() => { setError(null); setConfirmingFor(history.snapshot?.policy.userId ?? null) }}>{label}</Button>
    <ConfirmDialog open={open} title={`${label}?`} message={`Remove ${recordIds.length} record${recordIds.length === 1 ? '' : 's'} only from your history in this browser? The driver’s records and other contacts’ history are kept. This cannot be undone here. ${error ?? ''}`} confirmLabel={label} cancelLabel="Cancel" destructive busy={history.busy} onConfirm={() => { if (open) void confirm() }} onClose={() => setConfirmingFor(null)} />
  </>
}
