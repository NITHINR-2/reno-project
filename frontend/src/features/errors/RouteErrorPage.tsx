import { useRouteError } from 'react-router'
import { ErrorState } from '@/components/ui/ErrorState'

/** Router-level fallback for errors thrown outside the shell's own boundary. */
export function RouteErrorPage() {
  const error = useRouteError()
  return (
    <div className="flex min-h-screen items-center justify-center">
      <ErrorState title="Something went wrong" error={error} onRetry={() => window.location.reload()} />
    </div>
  )
}
