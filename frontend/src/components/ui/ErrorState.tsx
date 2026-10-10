import { AlertTriangle, RefreshCw } from 'lucide-react'
import { toProblem } from '@/lib/problem'
import { Button } from './Button'

export function ErrorState({
  title = 'Something went wrong',
  error,
  onRetry,
}: {
  title?: string
  error?: unknown
  onRetry?: () => void
}) {
  const detail = error === undefined ? undefined : toProblem(error).detail
  return (
    <div role="alert" className="flex flex-col items-center justify-center px-4 py-12 text-center">
      <div className="mb-3 rounded-full bg-red-50 p-3 text-red-700 dark:bg-red-950 dark:text-red-300">
        <AlertTriangle className="size-6" aria-hidden="true" />
      </div>
      <p className="font-medium text-fg">{title}</p>
      {detail && <p className="mt-1 max-w-sm text-sm text-fg-muted">{detail}</p>}
      {onRetry && (
        <Button variant="secondary" className="mt-4" icon={<RefreshCw className="size-4" aria-hidden="true" />} onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  )
}
