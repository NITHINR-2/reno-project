import type { ReactNode } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router'
import { ErrorState } from '@/components/ui/ErrorState'
import { Skeleton } from '@/components/ui/Skeleton'
import { AuthUserContext, hasPermission, useAuthUser, useCurrentUser } from '@/features/auth/useAuth'
import { DEALER_HOME, navFor } from './navigation'

function FullPageLoading() {
  return (
    <div role="status" aria-label="Loading your session" className="flex min-h-screen">
      <div className="hidden w-64 bg-charcoal lg:block" />
      <div className="flex-1 space-y-4 p-6">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-40 w-full" />
      </div>
    </div>
  )
}

/** Only signed-in users pass; everyone else goes to /login and comes back afterwards. */
export function RequireAuth() {
  const { data: user, isPending, error, refetch } = useCurrentUser()
  const location = useLocation()

  if (isPending) return <FullPageLoading />
  if (error) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <ErrorState title="Couldn't load your session" error={error} onRetry={() => void refetch()} />
      </div>
    )
  }
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  return (
    <AuthUserContext.Provider value={user}>
      <Outlet />
    </AuthUserContext.Provider>
  )
}

/** UX mirror of the server's checks: an unpermitted URL redirects to the 403 page. */
export function RequirePermission({ anyOf, children }: { anyOf: string[]; children: ReactNode }) {
  const user = useAuthUser()
  const location = useLocation()
  if (!hasPermission(user, ...anyOf)) {
    // Dealers have no HQ modules: send them back to the portal instead of a 403 page.
    if (hasPermission(user, 'DEALER_PORTAL')) return <Navigate to={DEALER_HOME} replace />
    return <Navigate to="/forbidden" replace state={{ from: location.pathname }} />
  }
  return children
}

/** `/` lands on the first module the user can open. */
export function HomeRedirect() {
  const [first] = navFor(useAuthUser())
  return <Navigate to={first ? first.to : '/forbidden'} replace />
}
