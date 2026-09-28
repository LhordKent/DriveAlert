import { useEffect, useRef, useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { CircleUserRound, FileText, Gauge, Info, LogOut, Menu, Settings, UsersRound, X } from 'lucide-react'
import { Brand } from '../../components/Brand'
import { usePortal } from '../providers/portalContext'
import { authRepository } from '../../repositories/authRepository'

const primaryNavigation = [
  { to: '/dashboard', label: 'Dashboard', icon: Gauge },
  { to: '/drivers', label: 'Drivers', icon: UsersRound },
  { to: '/requests', label: 'Requests', icon: FileText, badge: true },
]
const secondaryNavigation = [
  { to: '/account', label: 'Account', icon: CircleUserRound },
  { to: '/settings', label: 'Settings', icon: Settings },
  { to: '/about', label: 'About', icon: Info },
]

function Navigation({ onNavigate }: { onNavigate?: () => void }) {
  const { incomingRequests } = usePortal()
  const renderItem = ({ to, label, icon: Icon, badge = false }: (typeof primaryNavigation)[number]) => <NavLink key={to} to={to} onClick={onNavigate} className={({ isActive }) => `flex min-h-12 items-center gap-3 rounded-control px-3.5 text-sm font-semibold transition-colors ${isActive ? 'bg-signal-soft text-primary' : 'text-secondary hover:bg-control hover:text-primary'}`}><Icon className="size-5" aria-hidden="true" /><span className="flex-1">{label}</span>{badge && incomingRequests.length > 0 && <span className="min-w-6 rounded-full bg-signal px-1.5 py-0.5 text-center text-xs text-primary" aria-label={`${incomingRequests.length} pending requests`}>{incomingRequests.length}</span>}</NavLink>
  return <nav aria-label="Main navigation" className="flex h-full flex-col"><div className="space-y-1">{primaryNavigation.map(renderItem)}</div><div className="my-5 border-t border-border-soft" /><div className="space-y-1">{secondaryNavigation.map(renderItem)}</div><button type="button" onClick={() => authRepository.logout()} className="mt-auto flex min-h-12 items-center gap-3 rounded-control px-3.5 text-sm font-semibold text-secondary hover:bg-control hover:text-primary"><LogOut className="size-5" aria-hidden="true" />Log out</button></nav>
}

export function AppShell() {
  const [menuOpen, setMenuOpen] = useState(false)
  const closeButtonRef = useRef<HTMLButtonElement>(null)
  const location = useLocation()
  useEffect(() => setMenuOpen(false), [location.pathname])
  useEffect(() => {
    if (!menuOpen) return
    closeButtonRef.current?.focus()
    const close = (event: KeyboardEvent) => event.key === 'Escape' && setMenuOpen(false)
    document.addEventListener('keydown', close)
    return () => document.removeEventListener('keydown', close)
  }, [menuOpen])

  return <div className="min-h-screen bg-ink text-primary lg:grid lg:grid-cols-[17rem_1fr]">
    <a href="#main-content" className="fixed left-4 top-4 z-[60] -translate-y-24 rounded-control bg-primary px-4 py-3 text-sm font-semibold text-ink transition-transform focus:translate-y-0">Skip to main content</a>
    <aside className="fixed inset-y-0 left-0 z-30 hidden w-[17rem] border-r border-border-soft bg-raised p-5 lg:block"><Brand /><div className="mt-8 h-[calc(100%-5rem)]"><Navigation /></div></aside>
    <header className="sticky top-0 z-20 flex min-h-16 items-center justify-between border-b border-border-soft bg-raised/95 px-4 lg:hidden"><Brand /><button type="button" aria-label="Open navigation" aria-expanded={menuOpen} onClick={() => setMenuOpen(true)} className="grid size-12 place-items-center rounded-control text-primary hover:bg-control"><Menu aria-hidden="true" /></button></header>
    {menuOpen && <div className="fixed inset-0 z-40 bg-ink/80 lg:hidden" onMouseDown={(event) => event.currentTarget === event.target && setMenuOpen(false)}><aside role="dialog" aria-modal="true" aria-label="Navigation menu" className="h-full w-[min(88vw,20rem)] border-r border-border bg-raised p-5"><div className="flex items-center justify-between"><Brand /><button ref={closeButtonRef} type="button" aria-label="Close navigation" onClick={() => setMenuOpen(false)} className="grid size-12 place-items-center rounded-control hover:bg-control"><X aria-hidden="true" /></button></div><div className="mt-7 h-[calc(100%-5rem)]"><Navigation onNavigate={() => setMenuOpen(false)} /></div></aside></div>}
    <main id="main-content" tabIndex={-1} className="min-w-0 lg:col-start-2"><div className="mx-auto w-full max-w-[92rem] px-4 py-6 sm:px-7 sm:py-8 xl:px-10"><Outlet /></div></main>
  </div>
}
