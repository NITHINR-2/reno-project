import { AnimatePresence, motion } from 'motion/react'
import type { ReactNode } from 'react'
import { cn } from '@/lib/cn'
import { statusLabel } from '@/lib/format'
import { fade } from '@/lib/motion'

export type Tone = 'neutral' | 'info' | 'success' | 'warning' | 'danger' | 'accent'

const tones: Record<Tone, string> = {
  neutral: 'bg-gray-100 text-gray-800 ring-gray-300 dark:bg-gray-800 dark:text-gray-200 dark:ring-gray-600',
  info: 'bg-blue-50 text-blue-800 ring-blue-200 dark:bg-blue-950 dark:text-blue-200 dark:ring-blue-800',
  success: 'bg-green-50 text-green-800 ring-green-200 dark:bg-green-950 dark:text-green-200 dark:ring-green-800',
  warning: 'bg-amber-50 text-amber-900 ring-amber-200 dark:bg-amber-950 dark:text-amber-200 dark:ring-amber-800',
  danger: 'bg-red-50 text-red-800 ring-red-200 dark:bg-red-950 dark:text-red-200 dark:ring-red-800',
  accent: 'bg-yellow-50 text-yellow-900 ring-yellow-300 dark:bg-yellow-950 dark:text-yellow-200 dark:ring-yellow-700',
}

export function Badge({ tone = 'neutral', children, className }: { tone?: Tone; children: ReactNode; className?: string }) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-medium whitespace-nowrap ring-1 ring-inset',
        tones[tone],
        className,
      )}
    >
      {children}
    </span>
  )
}

const STATUS_TONES: Record<string, Tone> = {
  PENDING_MARKETING_REVIEW: 'info',
  RETURNED_TO_MARKETING: 'warning',
  MARKETING_QUALIFIED: 'info',
  PENDING_EXECUTIVE_APPROVAL: 'accent',
  EXECUTIVE_APPROVED: 'success',
  PENDING_FINANCE_CLEARANCE: 'accent',
  QUOTATION_ISSUED: 'info',
  PAYMENT_PENDING: 'warning',
  FUNDS_VERIFIED: 'success',
  FINANCIALLY_CLEARED: 'success',
  PRODUCTION_SCHEDULED: 'info',
  DISPATCH_SCHEDULED: 'info',
  COMPLETED: 'success',
  CANCELLED: 'danger',
}

/** Workflow status badge; cross-fades when the status changes. */
export function StatusBadge({ status }: { status: string }) {
  return (
    <span className="inline-grid">
      <AnimatePresence mode="popLayout" initial={false}>
        <motion.span key={status} variants={fade} initial="initial" animate="animate" exit="exit" className="[grid-area:1/1]">
          <Badge tone={STATUS_TONES[status] ?? 'neutral'}>{statusLabel(status)}</Badge>
        </motion.span>
      </AnimatePresence>
    </span>
  )
}
