import { formatSpan } from '@/lib/format'
import type { SlaStatus } from './schemas'

/** "18h left", "Overdue 6h"; when the server hasn't flagged a breach yet but the clock has run out, "Due now". */
export function slaText(status: SlaStatus, dueAt: string, now: number) {
  const remaining = new Date(dueAt).getTime() - now
  if (status === 'BREACHED') return `Overdue ${formatSpan(Math.min(remaining, 0))}`
  return remaining > 0 ? `${formatSpan(remaining)} left` : 'Due now'
}

/** SLA filter options (URL `sla`); "Overdue" is BREACHED. */
export const SLA_FILTERS = [
  { value: 'BREACHED', label: 'Overdue' },
  { value: 'AT_RISK,BREACHED', label: 'At risk or overdue' },
  { value: 'AT_RISK', label: 'At risk' },
  { value: 'ON_TRACK', label: 'On track' },
] as const
