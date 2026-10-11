import { AlarmClock } from 'lucide-react'
import { cn } from '@/lib/cn'
import type { Stage } from '../filters'
import { useFleetSummary } from '../queries'

/** "3 at risk · 1 overdue" for the stage this view works on (server counts); clicking filters the table to them. */
export function SlaAlert({ stage, onShow }: { stage?: Stage; onShow: () => void }) {
  const { data } = useFleetSummary()
  if (!data) return null
  const counts = stage && stage !== 'active' ? data.slaByStage[stage] : { atRisk: data.slaAtRisk, breached: data.slaBreached }
  if (!counts) return null
  const total = counts.atRisk + counts.breached
  return (
    <button
      type="button"
      onClick={onShow}
      disabled={total === 0}
      aria-label={total === 0 ? 'No requests at risk' : `${counts.atRisk} at risk, ${counts.breached} overdue. Show them.`}
      className={cn(
        'inline-flex min-h-11 items-center gap-2 rounded-lg border px-3 text-sm font-medium transition-colors duration-150 focus-visible:outline-2 focus-visible:outline-accent',
        counts.breached > 0
          ? 'border-red-300 bg-red-50 text-red-800 hover:bg-red-100 dark:border-red-800 dark:bg-red-950 dark:text-red-200'
          : counts.atRisk > 0
            ? 'border-amber-300 bg-amber-50 text-amber-900 hover:bg-amber-100 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-200'
            : 'border-line bg-surface text-fg-muted',
      )}
    >
      <AlarmClock className="size-4" aria-hidden="true" />
      {total === 0 ? 'No requests at risk' : `${counts.atRisk} at risk · ${counts.breached} overdue`}
    </button>
  )
}
