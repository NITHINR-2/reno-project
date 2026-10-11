import type { FieldValues, Path, UseFormReturn } from 'react-hook-form'
import type { ToastApi } from '@/components/ui/useToast'
import { toProblem } from '@/lib/problem'

/** Toast the ProblemDetail and copy any field errors from a 400 onto the form. */
export function reportError<T extends FieldValues>(toast: ToastApi, error: unknown, form?: UseFormReturn<T>) {
  const problem = toProblem(error)
  if (form) {
    for (const [field, message] of Object.entries(problem.errors)) {
      form.setError(field as Path<T>, { type: 'server', message })
    }
  }
  const detail = problem.status === 409 ? `${problem.detail} The latest data has been reloaded.` : problem.detail
  toast.error(problem.title, detail)
}
