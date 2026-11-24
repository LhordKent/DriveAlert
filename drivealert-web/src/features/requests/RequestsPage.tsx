import { Link } from 'react-router-dom'
import { UserRound } from 'lucide-react'
import { usePortal } from '../../app/providers/portalContext'
import { useConnectionAction } from '../../hooks/useConnectionAction'
import { connectionRepository } from '../../repositories/connectionRepository'
import { PageHeader } from '../../components/PageHeader'
import { Button } from '../../components/Button'
import { EmptyState, ErrorState, InlineError, PageSkeleton } from '../../components/PageState'
import { formatDateTime } from '../../lib/date'

export function RequestsPage() {
  const { incomingRequests, connectionsStatus, connectionsError } = usePortal()
  const { processingIds, actionError, run } = useConnectionAction()
  if (connectionsStatus === 'loading') return <><PageHeader title="Requests" /><PageSkeleton rows={4} /></>
  if (connectionsStatus === 'error' && connectionsError) return <><PageHeader title="Requests" /><ErrorState error={connectionsError} /></>
  return <>
    <PageHeader title="Requests" description="Accept a Driver’s invitation to view future shared alerts." />
    {actionError && <div className="mb-5"><InlineError message={actionError.message} /></div>}
    <section className="max-w-2xl">
      <h2 className="section-title">Incoming requests</h2>
      <div className="mt-3">
        {incomingRequests.length === 0
          ? <EmptyState title="No incoming requests" message="Share your connection code with a Driver so they can invite you."
              action={<Link to="/account" className="font-semibold text-primary underline decoration-border underline-offset-4">View my connection code</Link>} />
          : <div className="surface divide-y divide-border-soft">
              {incomingRequests.map((request) => {
                const id = request.connectionId
                const busy = processingIds.has(id)
                return <article key={id} className="p-5">
                  <div className="flex gap-3">
                    <span className="grid size-10 shrink-0 place-items-center rounded-full bg-strong text-secondary"><UserRound className="size-5" aria-hidden="true" /></span>
                    <div className="min-w-0">
                      <h3 className="font-semibold text-primary">{request.driverName || 'DriveAlert Driver'}</h3>
                      {request.driverEmail && <p className="mt-1 truncate text-sm text-secondary">{request.driverEmail}</p>}
                      <p className="mt-1 text-xs text-muted">Requested {formatDateTime(request.requestedAt)}</p>
                    </div>
                  </div>
                  <div className="mt-5 flex flex-col gap-2 sm:flex-row">
                    <Button busy={busy} onClick={() => run(id, () => connectionRepository.accept(id))}>Accept</Button>
                    <Button variant="secondary" disabled={busy} onClick={() => run(id, () => connectionRepository.decline(id))}>Decline</Button>
                  </div>
                </article>
              })}
            </div>}
      </div>
    </section>
  </>
}
