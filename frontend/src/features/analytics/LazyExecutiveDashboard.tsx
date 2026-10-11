import { lazy, Suspense } from 'react'
import { Skeleton } from '@/components/ui/Skeleton'

// Charts (Recharts) load only when the dashboard is opened, keeping them out of the main bundle.
const ExecutiveDashboardPage = lazy(() => import('./ExecutiveDashboardPage').then((m) => ({ default: m.ExecutiveDashboardPage })))

export function LazyExecutiveDashboard() {
  return (
    <Suspense fallback={<Skeleton className="h-96 rounded-xl" />}>
      <ExecutiveDashboardPage />
    </Suspense>
  )
}
