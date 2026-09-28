import type { ReactNode } from 'react'

export function PageHeader({ eyebrow, title, description, action }: { eyebrow?: string; title: string; description?: string; action?: ReactNode }) {
  return <header className="mb-7 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between"><div className="min-w-0">{eyebrow && <p className="mb-2 text-xs font-semibold uppercase tracking-[0.16em] text-signal">{eyebrow}</p>}<h1 className="page-title break-words">{title}</h1>{description && <p className="mt-2 max-w-3xl break-words text-sm leading-6 text-secondary">{description}</p>}</div>{action && <div className="shrink-0">{action}</div>}</header>
}
