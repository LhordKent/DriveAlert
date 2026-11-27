import { forwardRef, type InputHTMLAttributes } from 'react'
import type { FieldError } from 'react-hook-form'

export const FormField = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement> & { label: string; error?: FieldError; hint?: string }>(
  function FormField({ label, error, hint, ...props }, ref) {
    const descriptionId = error ? `${props.id}-error` : hint ? `${props.id}-hint` : props['aria-describedby']
    return <div>
      <label className="block">
        <span className="field-label">{label}</span>
        <input ref={ref} {...props} aria-invalid={!!error} aria-describedby={descriptionId} className={`field ${error ? 'border-signal' : ''}`} />
      </label>
      {error && <p id={descriptionId} className="mt-1.5 text-xs text-signal">{error.message}</p>}
      {!error && hint && <p id={descriptionId} className="mt-1.5 text-xs text-secondary">{hint}</p>}
    </div>
  },
)
