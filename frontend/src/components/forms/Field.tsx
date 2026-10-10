import { useId, type ReactNode } from 'react'
import { useFormContext, type FieldError, type FieldErrors } from 'react-hook-form'

export const inputClass =
  'block min-h-11 w-full rounded-lg border border-line bg-surface px-3 py-2 text-sm text-fg shadow-sm transition-colors duration-150 ' +
  'placeholder:text-fg-muted/70 focus-visible:border-accent-strong focus-visible:outline-2 focus-visible:outline-accent ' +
  'aria-invalid:border-red-500 disabled:opacity-60'

function errorAt(errors: FieldErrors, name: string): FieldError | undefined {
  let node: unknown = errors
  for (const part of name.split('.')) {
    node = node && typeof node === 'object' ? (node as Record<string, unknown>)[part] : undefined
  }
  return node as FieldError | undefined
}

type ControlProps = { id: string; name: string; invalid: boolean; describedBy: string | undefined }

export type FieldProps = { name: string; label: string; hint?: string; required?: boolean }

/** Label, hint and error wiring shared by every form control. */
export function Field({ name, label, hint, required, children }: FieldProps & { children: (props: ControlProps) => ReactNode }) {
  const { formState } = useFormContext()
  const id = useId()
  const error = errorAt(formState.errors, name)
  const hintId = hint ? `${id}-hint` : undefined
  const errorId = error ? `${id}-error` : undefined
  const describedBy = [hintId, errorId].filter(Boolean).join(' ') || undefined

  return (
    <div>
      <label htmlFor={id} className="mb-1 block text-sm font-medium text-fg">
        {label}
        {required && <span className="ml-0.5 text-red-600" aria-hidden="true">*</span>}
      </label>
      {children({ id, name, invalid: !!error, describedBy })}
      {hint && !error && (
        <p id={hintId} className="mt-1 text-xs text-fg-muted">
          {hint}
        </p>
      )}
      {error?.message && (
        <p id={errorId} className="mt-1 text-sm text-red-600 dark:text-red-400">
          {error.message}
        </p>
      )}
    </div>
  )
}
