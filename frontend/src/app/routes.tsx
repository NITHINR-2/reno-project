import type { RouteObject } from 'react-router'
import { AppShell } from '@/components/layout/AppShell'
import { AdminConsolePage } from '@/features/admin/AdminConsolePage'
import { LoginPage } from '@/features/auth/LoginPage'
import { ForbiddenPage } from '@/features/errors/ForbiddenPage'
import { NotFoundPage } from '@/features/errors/NotFoundPage'
import { RouteErrorPage } from '@/features/errors/RouteErrorPage'
import { HealthPage } from '@/features/health/HealthPage'
import { FleetWorkspace } from '@/features/fleet/FleetWorkspace'
import type { Stage } from '@/features/fleet/filters'
import { ModulePage } from '@/features/modules/ModulePage'
import { HomeRedirect, RequireAuth, RequirePermission } from './guards'
import { NAV_ITEMS } from './navigation'

const DESCRIPTIONS: Record<string, string> = {
  '/fleet-evaluation': 'Evaluate fleet requests, map yard stock and compute production needs.',
  '/executive-approval': 'Approve or reject marketing qualification and review deposits.',
  '/executive-dashboard': 'Order value, pipeline and SLA performance.',
  '/finance': 'GST quotations, payment receipts and financial clearance.',
  '/operations': 'Schedule production and dispatch, and complete deliveries.',
  '/admin': 'Users, roles, dealer onboarding and audit logs.',
}

/** Fleet modules open the shared workspace filtered to their stage; the rest are still placeholders. */
const FLEET_STAGES: Record<string, Stage> = {
  '/fleet-evaluation': 'marketing',
  '/executive-approval': 'executive',
  '/finance': 'finance',
  '/operations': 'operations',
}

const moduleRoutes: RouteObject[] = NAV_ITEMS.map((item) => ({
  path: item.to.slice(1),
  handle: { crumb: item.label },
  element: (
    <RequirePermission anyOf={item.anyOf}>
      {item.to === '/admin' ? (
        <AdminConsolePage />
      ) : FLEET_STAGES[item.to] ? (
        <FleetWorkspace key={item.to} title={item.label} description={DESCRIPTIONS[item.to]} defaultStage={FLEET_STAGES[item.to]} />
      ) : (
        <ModulePage item={item} description={DESCRIPTIONS[item.to]} />
      )}
    </RequirePermission>
  ),
}))

export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage />, errorElement: <RouteErrorPage /> },
  { path: '/status', element: <HealthPage />, errorElement: <RouteErrorPage /> },
  {
    element: <RequireAuth />,
    errorElement: <RouteErrorPage />,
    children: [
      {
        path: '/',
        element: <AppShell />,
        handle: { crumb: 'Fleet Portal' },
        children: [
          { index: true, element: <HomeRedirect /> },
          ...moduleRoutes,
          { path: 'forbidden', handle: { crumb: 'Access denied' }, element: <ForbiddenPage /> },
          { path: '*', handle: { crumb: 'Not found' }, element: <NotFoundPage /> },
        ],
      },
    ],
  },
]
