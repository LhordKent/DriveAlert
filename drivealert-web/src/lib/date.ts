import type { Timestamp } from 'firebase/firestore'

export function formatDateTime(value: Timestamp | null | undefined, fallback = 'Not available'): string {
  if (!value) return fallback
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(value.toDate())
}

export function formatRelativeTime(value: Timestamp | null | undefined, now = Date.now()): string {
  if (!value) return 'No shared activity yet'
  const difference = value.toMillis() - now
  const absolute = Math.abs(difference)
  const units: Array<[Intl.RelativeTimeFormatUnit, number]> = [
    ['day', 86_400_000],
    ['hour', 3_600_000],
    ['minute', 60_000],
  ]
  const [unit, milliseconds] = units.find(([, size]) => absolute >= size) ?? ['minute', 60_000]
  return new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' }).format(Math.round(difference / milliseconds), unit)
}
