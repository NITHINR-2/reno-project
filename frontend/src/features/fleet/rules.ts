import type { AuthUser } from '@/features/auth/api'
import { hasPermission } from '@/features/auth/useAuth'
import type { FleetRequestDetail, FleetStatus } from './api'

/**
 * UX mirror of the backend's permission and state rules. The server re-checks everything;
 * these only decide which controls are shown as editable.
 */
const FINANCE_RECEIPT_STAGES: FleetStatus[] = ['PENDING_FINANCE_CLEARANCE', 'PAYMENT_PENDING', 'FUNDS_VERIFIED']

export type DrawerTab = 'overview' | 'inventory' | 'finance' | 'payments' | 'workflow' | 'schedule' | 'audit'

export const TAB_LABELS: Record<DrawerTab, string> = {
  overview: 'Overview',
  inventory: 'Inventory',
  finance: 'Finance',
  payments: 'Payments',
  workflow: 'Workflow',
  schedule: 'Schedule',
  audit: 'Audit',
}

/** Tabs whose data the user may read (hidden tabs would only return 403). */
export function visibleTabs(user: AuthUser): DrawerTab[] {
  const tabs: DrawerTab[] = ['overview', 'inventory']
  if (hasPermission(user, 'QUOTATION_READ')) tabs.push('finance')
  if (hasPermission(user, 'PAYMENT_RECORD', 'DEPOSIT_REVIEW')) tabs.push('payments')
  tabs.push('workflow', 'schedule')
  if (hasPermission(user, 'AUDIT_LOG_READ')) tabs.push('audit')
  return tabs
}

export function editability(user: AuthUser, request: FleetRequestDetail) {
  const status = request.status
  const allowed = (action: string) => request.allowedActions.includes(action)
  const inReceiptStage = FINANCE_RECEIPT_STAGES.includes(status)
  return {
    details: hasPermission(user, 'FLEET_REQUEST_WRITE') && status === 'PENDING_MARKETING_REVIEW',
    stock: hasPermission(user, 'STOCK_MAP') && status === 'PENDING_MARKETING_REVIEW',
    quotation: hasPermission(user, 'QUOTATION_ISSUE') && status === 'PENDING_FINANCE_CLEARANCE',
    recordReceipt: hasPermission(user, 'PAYMENT_RECORD') && inReceiptStage,
    approveReceipt: hasPermission(user, 'PAYMENT_APPROVE') && inReceiptStage,
    // Scheduling guards (e.g. nothing to produce) are evaluated by the server into allowedActions.
    production: hasPermission(user, 'PRODUCTION_SCHEDULE') && allowed('SCHEDULE_PRODUCTION'),
    dispatch: hasPermission(user, 'DISPATCH_SCHEDULE') && allowed('SCHEDULE_DISPATCH'),
  }
}

export type Editability = ReturnType<typeof editability>
