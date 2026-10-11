import { AlarmClock } from 'lucide-react'
import { Badge, type Tone } from '@/components/ui/Badge'
import { cn } from '@/lib/cn'
import { formatDateTime } from '@/lib/format'
import { useNow } from '@/lib/useNow'
import type { SlaStatus } from '../schemas'
import { slaText } from '../sla'

const TONE: Record<SlaStatus, Tone> = { ON_TRACK: 'success', AT_RISK: 'warning', BREACHED: 'danger' }
const SPOKEN: Record<SlaStatus, string> = { ON_TRACK: 'on track', AT_RISK: 'at risk', BREACHED: 'overdue' }

/** Countdown chip: text from the local clock (ticks each minute), colour and status always from the server. */
export function SlaChip({ status, dueAt }: { status?: SlaStatus | null; dueAt?: string | null }) {
  const now = useNow()
  if (!status || !dueAt) return <span className="text-xs text-fg-muted">No SLA</span>
  const text = slaText(status, dueAt, now)
  return (
    <span title={`Due ${formatDateTime(dueAt)}`} aria-label={`SLA ${SPOKEN[status]}: ${text}`} className={cn('inline-flex', status === 'BREACHED' && 'sla-pulse')}>
      <Badge tone={TONE[status]}>
        <AlarmClock className="size-3" aria-hidden="true" />
        {text}
      </Badge>
    </span>
  )
}
