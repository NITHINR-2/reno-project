import { CalendarClock } from 'lucide-react'
import { AnimatePresence, motion } from 'motion/react'
import { Badge } from '@/components/ui/Badge'
import { formatDate, todayIST } from '@/lib/format'
import { fade } from '@/lib/motion'
import type { DealerStatus } from './api'
import { DEALER_STATUS_LABELS, DEALER_STATUS_TONES } from './statusMeta'

export function DealerStatusBadge({ status }: { status: DealerStatus }) {
  return (
    <span className="inline-grid">
      <AnimatePresence mode="popLayout" initial={false}>
        <motion.span key={status} variants={fade} initial="initial" animate="animate" exit="exit" className="[grid-area:1/1]">
          <Badge tone={DEALER_STATUS_TONES[status]}>{DEALER_STATUS_LABELS[status]}</Badge>
        </motion.span>
      </AnimatePresence>
    </span>
  )
}

/**
 * Dealers don't see HQ's internal SLA clocks; this chip shows the delivery date they asked for instead, and turns
 * amber once that date has passed on an order that isn't delivered or cancelled.
 */
export function ExpectedChip({ date, status }: { date: string | null | undefined; status: DealerStatus }) {
  if (!date) return <span className="text-sm text-fg-muted">No date requested</span>
  const closed = status === 'DELIVERED' || status === 'CANCELLED'
  const late = !closed && date < todayIST()
  return (
    <Badge tone={late ? 'warning' : 'neutral'}>
      <CalendarClock className="size-3.5" aria-hidden="true" />
      {late ? `Requested ${formatDate(date)} — passed` : `Requested ${formatDate(date)}`}
    </Badge>
  )
}
