import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import { usePortal } from '../../app/providers/portalContext'
import { Button } from '../../components/Button'
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { PageHeader } from '../../components/PageHeader'
import { PageSkeleton } from '../../components/PageState'
import { HISTORY_DAY_MS, shorterRetention } from '../../repositories/historyRepository'

export function HistorySettingsPage() {
  const { history, recordsByDriver } = usePortal()
  const savedDays = history.snapshot?.policy.days ?? null
  const [selection, setSelection] = useState('manual')
  const [custom, setCustom] = useState('')
  const [preview, setPreview] = useState<{ days: number; count: number; owner: string } | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  useEffect(() => {
    setSelection(savedDays === null ? 'manual' : [1, 3, 7].includes(savedDays) ? String(savedDays) : 'custom')
    setCustom(savedDays === null ? '' : String(savedDays))
  }, [savedDays, history.snapshot?.policy.userId])
  useEffect(() => { setPreview(null); setError(null); setMessage(null) }, [history.snapshot?.policy.userId])
  const days = selection === 'manual' ? null : selection === 'custom' ? Number(custom) : Number(selection)
  const valid = days === null || (custom !== '' || selection !== 'custom') && Number.isInteger(days) && days >= 1 && days <= 3650
  const save = async (next: number | null) => {
    setError(null)
    try { await history.save(next); setPreview(null); setMessage('History setting saved in this browser.') }
    catch { setError('History changes could not be saved. Enable browser storage, then try again.') }
  }
  const submit = () => {
    if (!valid) return
    setMessage(null); setError(null)
    if (days !== null && shorterRetention(savedDays, days)) {
      const cutoff = Date.now() - days * HISTORY_DAY_MS
      const count = Object.values(recordsByDriver).reduce((sum, state) => sum + state.records.filter((record) => record.periodStartedAt.toMillis() < cutoff).length, 0)
      if (history.snapshot) setPreview({ days, count, owner: history.snapshot.policy.userId })
    } else void save(days)
  }
  return <>
    <Link to="/settings" className="mb-5 inline-flex min-h-12 items-center gap-2 text-sm font-semibold text-secondary hover:text-primary"><ArrowLeft className="size-4" aria-hidden="true" />Back to Settings</Link>
    <PageHeader title="History & Storage" description="Choose how long to keep your shared history in this browser." />
    {history.status === 'loading' ? <PageSkeleton rows={3} /> : history.status === 'error' ? <div role="alert" className="max-w-2xl space-y-4"><p>{history.error}</p><Button variant="secondary" onClick={() => void history.refresh()}>Retry history settings</Button></div> : <form className="max-w-2xl" onSubmit={(event) => { event.preventDefault(); submit() }}>
      <fieldset disabled={history.busy} className="surface overflow-hidden">
        <legend className="sr-only">Automatic history deletion</legend>
        <div className="border-b border-border-soft px-5 py-4"><h2 className="section-title">Keep history for</h2><p className="mt-2 text-sm leading-6 text-secondary">A day is 24 hours from the original event, regardless of when it was synchronized.</p></div>
        <div className="divide-y divide-border-soft">{[['1', '1 day'], ['3', '3 days'], ['7', '7 days'], ['custom', 'Custom'], ['manual', 'Keep until I delete']].map(([value, label]) => <label key={value} className="flex min-h-14 cursor-pointer items-center gap-3 px-5 py-4 hover:bg-strong"><input className="size-5 shrink-0 accent-signal" type="radio" name="history-retention" value={value} checked={selection === value} onChange={() => setSelection(value)} /><span className="font-medium text-primary">{label}</span></label>)}</div>
      </fieldset>
      {selection === 'custom' && <div className="mt-5"><label htmlFor="history-days" className="field-label">Number of days</label><input id="history-days" type="number" inputMode="numeric" min={1} max={3650} step={1} required value={custom} onChange={(event) => setCustom(event.target.value)} disabled={history.busy} aria-describedby="history-days-help" aria-invalid={!valid || undefined} className="field max-w-xs" /><p id="history-days-help" className="mt-2 text-sm text-secondary">Enter 1 to 3,650 whole days.</p></div>}
      <div className="mt-5 space-y-3 text-sm leading-6 text-secondary">
        <p>{selection === 'manual' ? 'History stays in your view until you delete it. You can delete individual records or clear a driver’s history at any time.' : 'Older history is removed automatically while this portal is open, and when you return. This browser cannot run cleanup while it is closed.'}</p>
        <p>Removals affect only your view in this browser. The driver’s records and other contacts’ history remain unchanged. Choosing a longer period will not restore removed records.</p>
        <p>Android and other browsers keep separate choices. Clearing this site’s browser data resets your choices and personal removals.</p>
      </div>
      {error && !preview && <p role="alert" className="mt-4 text-sm text-signal">{error}</p>}
      {message && <p role="status" className="mt-4 text-sm text-primary">{message}</p>}
      <Button className="mt-6 w-full sm:w-auto" type="submit" busy={history.busy} disabled={!valid || days === savedDays}>Save</Button>
    </form>}
    <ConfirmDialog open={preview !== null && preview.owner === history.snapshot?.policy.userId && history.status === 'ready'} title="Apply automatic deletion?" message={`This removes ${preview?.count ?? 0} record${preview?.count === 1 ? '' : 's'} from currently loaded history. Older shared records received later will also be hidden. This cannot be undone here. ${error ?? ''}`} confirmLabel="Save and apply" cancelLabel="Cancel" destructive busy={history.busy} onConfirm={() => { if (preview && preview.owner === history.snapshot?.policy.userId) void save(preview.days) }} onClose={() => { setPreview(null); setError(null) }} />
  </>
}
