import type { ButtonHTMLAttributes, ReactNode } from 'react'

type Variant = 'primary' | 'secondary' | 'quiet' | 'danger'
const variants: Record<Variant, string> = {
  primary: 'bg-signal text-ink hover:bg-signal-pressed disabled:bg-signal/40',
  secondary: 'border border-border bg-control text-primary hover:bg-strong disabled:opacity-45',
  quiet: 'text-secondary hover:bg-control hover:text-primary disabled:opacity-45',
  danger: 'border border-signal/45 bg-signal-soft text-primary hover:bg-signal/30 disabled:opacity-45',
}

export function Button({ children, variant = 'primary', busy = false, className = '', disabled, ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { children: ReactNode; variant?: Variant; busy?: boolean }) {
  return <button {...props} disabled={disabled || busy} aria-busy={busy || undefined} className={`inline-flex min-h-12 items-center justify-center gap-2 rounded-control px-4 py-2.5 text-sm font-semibold transition-colors duration-200 ease-out disabled:cursor-not-allowed ${variants[variant]} ${className}`}>
    {busy && <span className="size-4 animate-spin rounded-full border-2 border-current border-r-transparent" aria-hidden="true" />}{children}
  </button>
}
