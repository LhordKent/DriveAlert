import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArrowLeft, CheckCircle2, Search, UserRound } from 'lucide-react'
import { PageHeader } from '../../components/PageHeader'
import { Button } from '../../components/Button'
import { InlineError } from '../../components/PageState'
import { usePortal } from '../../app/providers/portalContext'
import { connectionRepository, type ResolvedConnectionCode } from '../../repositories/connectionRepository'
import { formatConnectionCode, normalizeConnectionCode } from '../../lib/connectionCode'
import { toPortalError } from '../../lib/errors'

export function InviteDriverPage() {
  const navigate = useNavigate()
  const { profile } = usePortal()
  const [code, setCode] = useState('')
  const [resolved, setResolved] = useState<ResolvedConnectionCode | null>(null)
  const [error, setError] = useState('')
  const [lookingUp, setLookingUp] = useState(false)
  const [sending, setSending] = useState(false)
  const lookup = async () => {
    setError(''); setResolved(null); setLookingUp(true)
    try {
      const target = await connectionRepository.resolveCode(code)
      if (target.userId === profile?.uid) throw new Error("Use another person's connection code.")
      setResolved(target)
    } catch (value) { setError(toPortalError(value).message) } finally { setLookingUp(false) }
  }
  const send = async () => {
    if (!profile || !resolved) return
    setError(''); setSending(true)
    try { await connectionRepository.requestDriver(profile, resolved); navigate('/requests', { replace: true }) } catch (value) { setError(toPortalError(value).message) } finally { setSending(false) }
  }
  return <><button type="button" onClick={() => navigate(-1)} className="mb-5 inline-flex min-h-12 items-center gap-2 text-sm font-semibold text-secondary hover:text-primary"><ArrowLeft className="size-4" />Back</button><PageHeader title="Invite a Driver" description="Enter the exact code from the Driver's DriveAlert Account screen. They must approve before future eligible Stage 3 records are shared." /><div className="max-w-2xl surface p-5 sm:p-7"><div aria-live="polite">{error && <div className="mb-5"><InlineError message={error} /></div>}</div><label className="block"><span className="field-label">DriveAlert connection code</span><input value={code} onChange={(event) => { setCode(event.target.value.toUpperCase()); setResolved(null); setError('') }} placeholder="DA-XXXXX-XXXXX-XXXXX-XXXXX-XXXXXX" className="field font-mono tracking-wide" autoComplete="off" aria-describedby="code-help" /></label><p id="code-help" className="mt-2 text-xs leading-5 text-muted">Hyphens and spaces are optional. DriveAlert performs one exact code lookup and never lists accounts.</p>{!resolved ? <Button type="button" variant="secondary" busy={lookingUp} disabled={!normalizeConnectionCode(code)} onClick={lookup} className="mt-5"><Search className="size-4" />Find Driver</Button> : <div className="mt-6 border-t border-border-soft pt-6" aria-live="polite"><div className="flex items-start gap-4"><span className="grid size-11 shrink-0 place-items-center rounded-full bg-success-soft text-success"><UserRound className="size-5" /></span><div><p className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.12em] text-success"><CheckCircle2 className="size-4" />Account found</p><h2 className="mt-2 break-words text-lg font-semibold">{resolved.displayName}</h2><p className="mt-1 break-all font-mono text-xs text-muted">{formatConnectionCode(resolved.normalizedCode)}</p></div></div><p className="mt-5 text-sm leading-6 text-secondary">Confirm that this is the Driver you know. Sending creates or reuses the deterministic relationship record and waits for their approval.</p><div className="mt-5 flex flex-col gap-2 sm:flex-row"><Button type="button" busy={sending} onClick={send}>Send connection request</Button><Button type="button" variant="quiet" disabled={sending} onClick={() => setResolved(null)}>Use a different code</Button></div></div>}</div></>
}
