import type { DrawerTab, Editability } from './rules'

type Tone = 'primary' | 'danger' | 'secondary'

/** A transition done with one POST (and a ConfirmDialog, since none can be undone). */
export type TransitionAction = {
  kind: 'transition'
  label: string
  endpoint: string
  tone: Tone
  requireReason?: boolean
  confirmTitle: string
  confirmText: string
  success: string
}

/** An action that needs a form in one of the drawer tabs; the footer button just opens that tab. */
export type TabAction = { kind: 'tab'; label: string; tab: DrawerTab }

/**
 * Buttons for the server's `allowedActions` (role + state + guards, evaluated by the backend).
 * Actions not listed here (AWAIT_PAYMENT, VERIFY_FUNDS) are performed by the system, and CANCEL
 * belongs to the Supervisor override phase. ISSUE_QUOTATION is never offered beforehand (its guard
 * needs an issued quotation), so the quotation and receipt shortcuts come from `tabShortcuts` instead.
 */
export const WORKFLOW_ACTIONS: Record<string, TransitionAction | TabAction> = {
  RESUBMIT: {
    kind: 'transition',
    label: 'Resubmit for review',
    endpoint: 'resubmit',
    tone: 'primary',
    confirmTitle: 'Resubmit this request?',
    confirmText: 'It returns to pending marketing review so details and stock can be revised.',
    success: 'Resubmitted for marketing review',
  },
  QUALIFY: {
    kind: 'transition',
    label: 'Qualify',
    endpoint: 'submit-for-qualification',
    tone: 'primary',
    confirmTitle: 'Qualify this request?',
    confirmText: 'Details and stock mapping are locked once the request is qualified.',
    success: 'Request qualified',
  },
  FORWARD_TO_EXECUTIVE: {
    kind: 'transition',
    label: 'Forward to Marketing Head',
    endpoint: 'forward-to-marketing-head',
    tone: 'primary',
    confirmTitle: 'Forward to the Marketing Head?',
    confirmText: 'The request moves to the executive approval queue.',
    success: 'Forwarded to the Marketing Head',
  },
  APPROVE: {
    kind: 'transition',
    label: 'Approve',
    endpoint: 'approve',
    tone: 'primary',
    confirmTitle: 'Approve this request?',
    confirmText: 'The marketing qualification is approved; next, review deposits and forward to Finance.',
    success: 'Request approved',
  },
  REJECT: {
    kind: 'transition',
    label: 'Reject',
    endpoint: 'reject',
    tone: 'danger',
    requireReason: true,
    confirmTitle: 'Reject and return to Marketing?',
    confirmText: 'Marketing sees your reason and can revise and resubmit.',
    success: 'Returned to Marketing',
  },
  FORWARD_TO_FINANCE: {
    kind: 'transition',
    label: 'Forward to Finance',
    endpoint: 'forward-to-finance',
    tone: 'primary',
    confirmTitle: 'Forward to Finance?',
    confirmText: 'Finance will calculate and issue the GST quotation.',
    success: 'Forwarded to Finance',
  },
  GRANT_CLEARANCE: {
    kind: 'transition',
    label: 'Grant clearance',
    endpoint: 'grant-clearance',
    tone: 'primary',
    confirmTitle: 'Grant financial clearance?',
    confirmText: 'Approved deposits cover the requirement. The request moves on to production and dispatch.',
    success: 'Financial clearance granted',
  },
  SCHEDULE_PRODUCTION: { kind: 'tab', label: 'Schedule production…', tab: 'schedule' },
  SCHEDULE_DISPATCH: { kind: 'tab', label: 'Schedule dispatch…', tab: 'schedule' },
  COMPLETE: {
    kind: 'transition',
    label: 'Mark completed',
    endpoint: 'complete',
    tone: 'primary',
    confirmTitle: 'Mark this request completed?',
    confirmText: 'Production orders and dispatches are closed and the allocated yard stock is consumed.',
    success: 'Request completed',
  },
}

/** Footer shortcuts to tab forms, from the same role + state rules that make those tabs editable. */
export function tabShortcuts(can: Editability): (TabAction & { code: string })[] {
  const shortcuts: (TabAction & { code: string })[] = []
  if (can.quotation) shortcuts.push({ code: 'QUOTATION', kind: 'tab', label: 'Quotation…', tab: 'finance' })
  if (can.recordReceipt || can.approveReceipt) shortcuts.push({ code: 'RECEIPTS', kind: 'tab', label: 'Receipts…', tab: 'payments' })
  return shortcuts
}
