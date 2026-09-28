import { Link } from 'react-router-dom'

export function Brand({ compact = false }: { compact?: boolean }) {
  return <Link to="/dashboard" className="inline-flex min-h-12 items-center gap-3 rounded-control text-primary" aria-label="DriveAlert dashboard">
    <img src="/drivealert-logo.png" alt="" className="size-10 object-contain" />
    {!compact && <span className="leading-none"><span className="block text-lg font-bold tracking-tight">DriveAlert</span><span className="mt-1 block text-[0.68rem] font-semibold uppercase tracking-[0.16em] text-muted">Trusted Contact</span></span>}
  </Link>
}
