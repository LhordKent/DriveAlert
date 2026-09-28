import { Link } from 'react-router-dom'
import { Plus, UserRound } from 'lucide-react'
import { usePortal } from '../../app/providers/portalContext'
import { useConnectionAction } from '../../hooks/useConnectionAction'
import { connectionRepository } from '../../repositories/connectionRepository'
import { PageHeader } from '../../components/PageHeader'
import { Button } from '../../components/Button'
import { EmptyState, ErrorState, InlineError, PageSkeleton } from '../../components/PageState'
import { formatDateTime } from '../../lib/date'
import type { ConnectionRequestView } from '../../models/domain'

export function RequestsPage() {
  const { incomingRequests, outgoingRequests, connectionsStatus, connectionsError } = usePortal()
  const { processingIds, actionError, run } = useConnectionAction()
  if (connectionsStatus === 'loading') return <><PageHeader title="Requests" /><PageSkeleton rows={4} /></>
  if (connectionsStatus === 'error' && connectionsError) return <><PageHeader title="Requests" /><ErrorState error={connectionsError} /></>
  return <><PageHeader title="Requests" description="Accepting a Driver shares future eligible Stage 3 records. Pending requests update in real time." action={<Link to="/requests/invite" className="inline-flex min-h-12 items-center gap-2 rounded-control bg-signal px-4 text-sm font-semibold text-primary hover:bg-signal-pressed"><Plus className="size-4" />Invite a Driver</Link>} />{actionError && <div className="mb-5"><InlineError message={actionError.message} /></div>}<div className="grid gap-9 xl:grid-cols-2"><RequestSection title="Incoming requests" emptyTitle="No incoming requests" emptyMessage="New Driver requests will appear here." requests={incomingRequests} renderActions={(request) => <div className="flex flex-col gap-2 sm:flex-row"><Button busy={processingIds.has(request.connection.connectionId)} onClick={() => run(request.connection.connectionId, () => connectionRepository.accept(request.connection.connectionId))}>Accept</Button><Button variant="secondary" disabled={processingIds.has(request.connection.connectionId)} onClick={() => run(request.connection.connectionId, () => connectionRepository.decline(request.connection.connectionId))}>Decline</Button></div>} /><RequestSection title="Outgoing requests" emptyTitle="No outgoing requests" emptyMessage="Requests you send to Drivers will appear here until they respond." requests={outgoingRequests} renderActions={(request) => <Button variant="secondary" busy={processingIds.has(request.connection.connectionId)} onClick={() => run(request.connection.connectionId, () => connectionRepository.cancel(request.connection.connectionId))}>Cancel request</Button>} /></div></>
}

function RequestSection({ title, emptyTitle, emptyMessage, requests, renderActions }: { title: string; emptyTitle: string; emptyMessage: string; requests: ConnectionRequestView[]; renderActions: (request: ConnectionRequestView) => React.ReactNode }) {
  return <section><h2 className="section-title">{title}</h2><div className="mt-3">{requests.length === 0 ? <EmptyState title={emptyTitle} message={emptyMessage} /> : <div className="surface divide-y divide-border-soft">{requests.map((request) => <article key={request.connection.connectionId} className="p-5"><div className="flex gap-3"><span className="grid size-10 shrink-0 place-items-center rounded-full bg-strong text-secondary"><UserRound className="size-5" /></span><div className="min-w-0"><h3 className="font-semibold text-primary">{request.connection.driverName || 'DriveAlert Driver'}</h3>{request.connection.driverEmail && <p className="mt-1 truncate text-sm text-secondary">{request.connection.driverEmail}</p>}<p className="mt-1 text-xs text-muted">Requested {formatDateTime(request.connection.requestedAt)}</p></div></div><div className="mt-5">{renderActions(request)}</div></article>)}</div>}</div></section>
}
