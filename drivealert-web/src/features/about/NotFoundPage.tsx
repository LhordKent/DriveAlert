import { Link } from 'react-router-dom'
import { SearchX } from 'lucide-react'

export function NotFoundPage() { return <main className="grid min-h-screen place-items-center bg-ink p-5"><div className="surface max-w-lg p-7 text-center"><SearchX className="mx-auto size-9 text-muted" /><h1 className="mt-5 text-xl font-bold">Page not found</h1><p className="mt-2 text-sm leading-6 text-secondary">This DriveAlert page does not exist or the address is malformed.</p><Link to="/" className="mt-6 inline-flex min-h-12 items-center rounded-control bg-signal px-5 text-sm font-semibold text-primary">Return to DriveAlert</Link></div></main> }
