import { Link } from 'react-router-dom'
import { ChevronRight, CircleUserRound, Info, LogOut } from 'lucide-react'
import { PageHeader } from '../../components/PageHeader'
import { authRepository } from '../../repositories/authRepository'

const rows = [
  { to: '/account', title: 'Account', detail: 'Profile, email, role, and connection code', icon: CircleUserRound },
  { to: '/about', title: 'About DriveAlert', detail: 'Purpose, Stage 3 sharing, privacy, and limitations', icon: Info },
]

export function SettingsPage() {
  return <><PageHeader title="Settings" description="Only settings backed by the current DriveAlert product are shown." /><div className="max-w-3xl space-y-6"><section className="surface divide-y divide-border-soft">{rows.map(({ to, title, detail, icon: Icon }) => <Link key={to} to={to} className="grid min-h-20 grid-cols-[auto_1fr_auto] items-center gap-4 px-5 py-3 hover:bg-strong"><Icon className="size-5 text-muted" /><span><span className="block font-semibold text-primary">{title}</span><span className="mt-1 block text-sm text-secondary">{detail}</span></span><ChevronRight className="size-5 text-muted" /></Link>)}</section><section><h2 className="section-title">Notifications</h2><div className="mt-3 rounded-panel border border-border-soft bg-raised p-5"><p className="text-sm font-semibold text-primary">Remote notifications are not available</p><p className="mt-2 text-sm leading-6 text-secondary">DriveAlert does not currently have a Trusted Contact push-notification backend. No switch is shown because it would imply delivery that does not exist.</p></div></section><button type="button" onClick={() => authRepository.logout()} className="inline-flex min-h-12 items-center gap-2 rounded-control border border-signal/40 bg-signal-soft px-4 text-sm font-semibold text-primary hover:bg-signal/30"><LogOut className="size-4" />Log out</button></div></>
}
