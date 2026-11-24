import { useState } from 'react'
import { Link } from 'react-router-dom'
import { PageHeader } from '../../components/PageHeader'
import { DriverCard } from '../../components/DriverCard'
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { EmptyState, ErrorState, InlineError, PageSkeleton } from '../../components/PageState'
import { usePortal } from '../../app/providers/portalContext'
import { useConnectionAction } from '../../hooks/useConnectionAction'
import { connectionRepository } from '../../repositories/connectionRepository'

export function DriversPage() {
  const { approvedDrivers, recordsByDriver, connectionsStatus, connectionsError } = usePortal()
  const [disconnectId, setDisconnectId] = useState<string | null>(null)
  const { processingIds, actionError, run } = useConnectionAction()
  const selected = approvedDrivers.find((driver) => driver.connectionId === disconnectId)
  return <><PageHeader title="Connected Drivers" description="Review alerts shared by your approved Drivers." />{actionError && <div className="mb-4"><InlineError message={actionError.message} /></div>}{connectionsStatus === 'loading' ? <PageSkeleton rows={3} /> : connectionsStatus === 'error' && connectionsError ? <ErrorState error={connectionsError} /> : approvedDrivers.length === 0 ? <EmptyState title="No connected Drivers" message="Accept a Driver’s invitation to review their future shared alerts." action={<Link to="/requests" className="font-semibold text-primary underline decoration-border underline-offset-4">Open requests</Link>} /> : <section className="grid gap-4 xl:grid-cols-2">{approvedDrivers.map((connection) => <DriverCard key={connection.connectionId} connection={connection} recordState={recordsByDriver[connection.driverUserId]} onDisconnect={() => setDisconnectId(connection.connectionId)} />)}</section>}<ConfirmDialog open={!!selected} title="Disconnect Driver?" message={`You will lose access to ${selected?.driverName || 'this Driver'}'s previously shared records. The Driver's local alert history will not change.`} confirmLabel="Disconnect" destructive busy={!!disconnectId && processingIds.has(disconnectId)} onClose={() => setDisconnectId(null)} onConfirm={async () => { if (!disconnectId) return; const ok = await run(disconnectId, () => connectionRepository.revoke(disconnectId)); if (ok) setDisconnectId(null) }} /></>
}
