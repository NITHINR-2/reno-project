import { Navigate, type RouteObject } from 'react-router'
import { AppShell } from '@/components/layout/AppShell'
import { AdminConsolePage } from '@/features/admin/AdminConsolePage'
import { LazyExecutiveDashboard } from '@/features/analytics/LazyExecutiveDashboard'
import { LoginPage } from '@/features/auth/LoginPage'
import { SetPasswordPage } from '@/features/auth/SetPasswordPage'
import { CataloguePage } from '@/features/dealer/catalogue/CataloguePage'
import { DealerLayout } from '@/features/dealer/DealerLayout'
import { OrderDetailPage } from '@/features/dealer/orders/OrderDetailPage'
import { OrdersPage } from '@/features/dealer/orders/OrdersPage'
import { ForbiddenPage } from '@/features/errors/ForbiddenPage'
import { NotFoundPage } from '@/features/errors/NotFoundPage'
import { RouteErrorPage } from '@/features/errors/RouteErrorPage'
import { HealthPage } from '@/features/health/HealthPage'
import { FleetWorkspace } from '@/features/fleet/FleetWorkspace'
import type { Stage } from '@/features/fleet/filters'
import { ModulePage } from '@/features/modules/ModulePage'
import { HomeRedirect, RequireAuth, RequirePermission } from './guards'
import { DEALER_HOME, isDealerItem, NAV_ITEMS } from './navigation'
import { QuotationVerifyPage } from '@/features/verify/QuotationVerifyPage'

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

const moduleRoutes: RouteObject[] = NAV_ITEMS.filter((item) => !isDealerItem(item)).map((item) => ({
  path: item.to.slice(1),
  handle: { crumb: item.label },
  element: (
    <RequirePermission anyOf={item.anyOf}>
      {item.to === '/admin' ? (
        <AdminConsolePage />
      ) : item.to === '/executive-dashboard' ? (
        <LazyExecutiveDashboard />
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
  // Public: the quotation QR code lands here; no session needed.
  { path: '/verify/quotation/:token', element: <QuotationVerifyPage />, errorElement: <RouteErrorPage /> },
  // Public: the one-time link from the Admin Console (token in the URL fragment).
  { path: '/set-password', element: <SetPasswordPage />, errorElement: <RouteErrorPage /> },
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
          // Dealer Portal: DEALER_PORTAL only (no HQ role holds it); the server scopes every call to the dealer.
          {
            path: 'dealer',
            handle: { crumb: 'Dealer Portal' },
            element: (
              <RequirePermission anyOf={['DEALER_PORTAL']}>
                <DealerLayout />
              </RequirePermission>
            ),
            children: [
              { index: true, element: <Navigate to={DEALER_HOME} replace /> },
              { path: 'catalogue', handle: { crumb: 'Catalogue' }, element: <CataloguePage /> },
              {
                path: 'orders',
                handle: { crumb: 'My Orders' },
                children: [
                  { index: true, element: <OrdersPage /> },
                  { path: ':orderId', handle: { crumb: 'Order' }, element: <OrderDetailPage /> },
                ],
              },
            ],
          },
          { path: 'forbidden', handle: { crumb: 'Access denied' }, element: <ForbiddenPage /> },
          { path: '*', handle: { crumb: 'Not found' }, element: <NotFoundPage /> },
        ],
      },
    ],
  },
]
