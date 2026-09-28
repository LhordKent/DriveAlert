import { forwardRef, type InputHTMLAttributes } from 'react'
import type { FieldError } from 'react-hook-form'

export const FormField = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement> & { label: string; error?: FieldError }>(
  function FormField({ label, error, ...props }, ref) {
    return <label className="block"><span className="field-label">{label}</span><input ref={ref} {...props} aria-invalid={!!error} aria-describedby={error ? `${props.id}-error` : undefined} className={`field ${error ? 'border-signal' : ''}`} />{error && <span id={`${props.id}-error`} className="mt-1.5 block text-xs text-signal">{error.message}</span>}</label>
  },
)
