import { CheckCircle2, CircleAlert, Clock3, Info } from 'lucide-react'

type Tone = 'success' | 'warning' | 'info' | 'neutral'
const tones: Record<Tone, string> = { success: 'bg-success-soft text-success', warning: 'bg-warning-soft text-warning', info: 'bg-info-soft text-info', neutral: 'bg-strong text-secondary' }
const icons = { success: CheckCircle2, warning: CircleAlert, info: Info, neutral: Clock3 }

export function StatusBadge({ label, tone = 'neutral' }: { label: string; tone?: Tone }) {
  const Icon = icons[tone]
  return <span className={`inline-flex min-h-7 items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold ${tones[tone]}`}><Icon className="size-3.5" aria-hidden="true" />{label}</span>
}
