import { useEffect, useRef } from 'react'
import { Button } from './Button'

export function ConfirmDialog({ open, title, message, confirmLabel, busy = false, destructive = false, onConfirm, onClose }: { open: boolean; title: string; message: string; confirmLabel: string; busy?: boolean; destructive?: boolean; onConfirm: () => void; onClose: () => void }) {
  const cancelRef = useRef<HTMLButtonElement>(null)
  const dialogRef = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (!open) return
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { document.body.style.overflow = previousOverflow }
  }, [open])
  useEffect(() => {
    if (!open) return
    const previouslyFocused = document.activeElement as HTMLElement | null
    cancelRef.current?.focus()
    const handleKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !busy) onClose()
      if (event.key !== 'Tab') return
      const controls = dialogRef.current?.querySelectorAll<HTMLElement>('button:not(:disabled), [href], input:not(:disabled)')
      if (!controls?.length) return
      const first = controls[0]
      const last = controls[controls.length - 1]
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
    }
    document.addEventListener('keydown', handleKey)
    return () => { document.removeEventListener('keydown', handleKey); previouslyFocused?.focus() }
  }, [open, busy, onClose])
  if (!open) return null
  return <div className="fixed inset-0 z-50 grid place-items-center bg-ink/80 p-4" onMouseDown={(event) => event.currentTarget === event.target && !busy && onClose()}><div ref={dialogRef} role="alertdialog" aria-modal="true" aria-labelledby="confirm-title" aria-describedby="confirm-description" className="surface w-full max-w-md border-border p-5 sm:p-6"><h2 id="confirm-title" className="text-lg font-semibold text-primary">{title}</h2><p id="confirm-description" className="mt-2 text-sm leading-6 text-secondary">{message}</p><div className="mt-6 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end"><button ref={cancelRef} type="button" onClick={onClose} disabled={busy} className="inline-flex min-h-12 items-center justify-center rounded-control border border-border bg-control px-4 py-2.5 text-sm font-semibold text-primary hover:bg-strong disabled:opacity-45">Keep connection</button><Button variant={destructive ? 'danger' : 'primary'} busy={busy} onClick={onConfirm}>{confirmLabel}</Button></div></div></div>
}
