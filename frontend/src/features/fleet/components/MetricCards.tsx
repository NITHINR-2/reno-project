import { BadgeCheck, ClipboardList, Factory, Landmark, Layers, Wrench, type LucideIcon } from 'lucide-react'
import { animate, motion, useReducedMotion } from 'motion/react'
import { useEffect, useState } from 'react'
import { Card } from '@/components/ui/Card'
import { ErrorState } from '@/components/ui/ErrorState'
import { Skeleton } from '@/components/ui/Skeleton'
import { cn } from '@/lib/cn'
import { formatCount } from '@/lib/format'
import { duration, ease, staggerDelay } from '@/lib/motion'
import type { FleetMetrics } from '../api'
import type { Stage } from '../filters'
import { useFleetSummary } from '../queries'

type Metric = { key: keyof FleetMetrics; label: string; icon: LucideIcon; stage?: Stage; hint: string }

const METRICS: Metric[] = [
  { key: 'activeRequests', label: 'Active requests', icon: Layers, stage: 'active', hint: 'Not completed or cancelled' },
  { key: 'inMarketing', label: 'Marketing review', icon: ClipboardList, stage: 'marketing', hint: 'Review, returned, qualified' },
  { key: 'inExecutiveApproval', label: 'Executive approval', icon: BadgeCheck, stage: 'executive', hint: 'Awaiting or approved' },
  { key: 'inFinance', label: 'Finance', icon: Landmark, stage: 'finance', hint: 'Quotation to funds verified' },
  { key: 'inOperations', label: 'Production & dispatch', icon: Factory, stage: 'operations', hint: 'Cleared to dispatch' },
  { key: 'unitsToProduce', label: 'Units to produce', icon: Wrench, hint: 'Across active requests' },
]

/** Counts up from 0 on first load; jumps straight to the value with reduced motion. */
function CountUp({ value }: { value: number }) {
  const reduce = useReducedMotion()
  const [shown, setShown] = useState(0)
  useEffect(() => {
    if (reduce) return
    const controls = animate(0, value, { duration: 0.6, ease: ease.out, onUpdate: (v) => setShown(Math.round(v)) })
    return () => controls.stop()
  }, [value, reduce])
  return <>{formatCount(reduce ? value : shown)}</>
}

export function MetricCards({ activeStage, onSelectStage }: { activeStage?: string; onSelectStage: (stage: Stage) => void }) {
  const { data, isPending, error, refetch } = useFleetSummary()

  if (error) {
    return (
      <Card className="mb-6">
        <ErrorState title="Couldn't load the summary" error={error} onRetry={() => void refetch()} />
      </Card>
    )
  }

  return (
    <section aria-label="Summary" className="mb-6 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6">
      {METRICS.map((metric, index) => {
        const Icon = metric.icon
        const selected = metric.stage !== undefined && activeStage === `stage:${metric.stage}`
        const body = (
          <>
            <div className="flex items-center justify-between gap-2">
              <span className="text-sm font-medium text-fg-muted">{metric.label}</span>
              <Icon className={cn('size-5', selected ? 'text-accent-strong' : 'text-fg-muted')} aria-hidden="true" />
            </div>
            <div className="mt-2 text-2xl font-bold tabular-nums text-fg">
              {isPending ? <Skeleton className="h-8 w-16" /> : <CountUp value={data[metric.key]} />}
            </div>
            <p className="mt-1 text-xs text-fg-muted">{metric.hint}</p>
          </>
        )
        const className = cn(
          'block h-full w-full rounded-xl border bg-surface p-4 text-left shadow-sm transition-shadow duration-150 hover:shadow-md',
          selected ? 'border-accent ring-1 ring-accent' : 'border-line',
        )
        return (
          <motion.div
            key={metric.key}
            initial={{ opacity: 0, y: 4 }}
            animate={{ opacity: 1, y: 0, transition: { duration: duration.base, ease: ease.out, delay: staggerDelay(index) } }}
            whileHover={{ y: -2 }}
          >
            {metric.stage ? (
              <button
                type="button"
                aria-pressed={selected}
                onClick={() => onSelectStage(metric.stage as Stage)}
                className={cn(className, 'focus-visible:outline-2 focus-visible:outline-accent')}
              >
                {body}
              </button>
            ) : (
              <div className={className}>{body}</div>
            )}
          </motion.div>
        )
      })}
    </section>
  )
}
