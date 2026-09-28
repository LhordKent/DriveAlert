import { CircleAlert, Inbox, RefreshCw, ShieldX, WifiOff } from 'lucide-react'
import { Button } from './Button'
import type { PortalError } from '../models/domain'

export function PageSkeleton({ rows = 3 }: { rows?: number }) {
  return <div aria-label="Loading" role="status" className="space-y-3">{Array.from({ length: rows }, (_, index) => <div key={index} className="surface animate-pulse p-5"><div className="h-4 w-2/5 rounded bg-strong" /><div className="mt-3 h-3 w-3/5 rounded bg-strong" /></div>)}<span className="sr-only">Loading DriveAlert data</span></div>
}

export function EmptyState({ title, message, action }: { title: string; message: string; action?: React.ReactNode }) {
  return <div className="surface px-5 py-10 text-center"><Inbox className="mx-auto size-8 text-muted" aria-hidden="true" /><h2 className="mt-4 text-base font-semibold text-primary">{title}</h2><p className="mx-auto mt-2 max-w-lg text-sm leading-6 text-secondary">{message}</p>{action && <div className="mt-5">{action}</div>}</div>
}

export function ErrorState({ error, onRetry }: { error: PortalError; onRetry?: () => void }) {
  const Icon = error.kind === 'permission' ? ShieldX : error.kind === 'offline' ? WifiOff : CircleAlert
  return <div role="alert" className="surface border-signal/45 bg-signal-soft/50 px-5 py-6"><Icon className="size-6 text-signal" aria-hidden="true" /><h2 className="mt-3 font-semibold text-primary">{error.kind === 'permission' ? 'Access changed' : 'DriveAlert could not load this page'}</h2><p className="mt-1 max-w-2xl text-sm leading-6 text-secondary">{error.message}</p>{onRetry && <Button variant="secondary" onClick={onRetry} className="mt-4"><RefreshCw className="size-4" />Retry</Button>}</div>
}

export function InlineError({ message }: { message: string }) { return <p role="alert" className="rounded-control bg-signal-soft px-3.5 py-3 text-sm text-primary">{message}</p> }
