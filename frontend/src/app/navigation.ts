import { BadgeCheck, ChartColumn, ClipboardList, Factory, Landmark, ShieldCheck, type LucideIcon } from 'lucide-react'
import type { AuthUser } from '@/features/auth/api'
import { hasPermission } from '@/features/auth/useAuth'

export type NavItem = {
  to: string
  label: string
  icon: LucideIcon
  /** Visible when the user holds any of these permissions (mirrors the backend; the server re-checks). */
  anyOf: string[]
}

/** One source for the sidebar, the route guards and the post-login landing page. */
export const NAV_ITEMS: NavItem[] = [
  { to: '/fleet-evaluation', label: 'Fleet Evaluation', icon: ClipboardList, anyOf: ['STOCK_MAP'] },
  { to: '/executive-approval', label: 'Executive Approval', icon: BadgeCheck, anyOf: ['EXECUTIVE_APPROVE'] },
  { to: '/executive-dashboard', label: 'Executive Dashboard', icon: ChartColumn, anyOf: ['EXECUTIVE_DASHBOARD_READ'] },
  { to: '/finance', label: 'Financial Clearance', icon: Landmark, anyOf: ['QUOTATION_ISSUE', 'PAYMENT_APPROVE', 'FINANCE_CLEARANCE'] },
  { to: '/operations', label: 'Production & Dispatch', icon: Factory, anyOf: ['PRODUCTION_SCHEDULE', 'DISPATCH_SCHEDULE'] },
  { to: '/admin', label: 'Admin Console', icon: ShieldCheck, anyOf: ['ADMIN_USERS_MANAGE', 'AUDIT_LOG_READ'] },
]

export function navFor(user: AuthUser | null | undefined): NavItem[] {
  return NAV_ITEMS.filter((item) => hasPermission(user, ...item.anyOf))
}

export function navItem(to: string): NavItem {
  const item = NAV_ITEMS.find((i) => i.to === to)
  if (!item) throw new Error(`Unknown nav item ${to}`)
  return item
}
