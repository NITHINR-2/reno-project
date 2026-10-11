import { ArrowDownRight, ArrowUpRight, Clock, Gauge, IndianRupee, ListChecks, Minus, TriangleAlert, type LucideIcon } from 'lucide-react'
import { motion } from 'motion/react'
import { CountUp } from '@/components/ui/CountUp'
import { Skeleton } from '@/components/ui/Skeleton'
import { cn } from '@/lib/cn'
import { formatCount, formatINR } from '@/lib/format'
import { duration, ease, staggerDelay } from '@/lib/motion'
import type { ExecutiveAnalytics, Kpi } from '../api'
import { formatCompactINR, formatHours } from './chartKit'

type KpiKey = keyof ExecutiveAnalytics['kpis']
type Def = { key: KpiKey; label: string; icon: LucideIcon; format: (v: number) => string; full?: (v: number) => string; higherIsBetter: boolean; hint: string }

const KPIS: Def[] = [
  { key: 'totalOrderValue', label: 'Total order value', icon: IndianRupee, format: formatCompactINR, full: formatINR, higherIsBetter: true, hint: 'Before GST, excluding cancelled' },
  { key: 'requestCount', label: 'Requests', icon: ListChecks, format: formatCount, higherIsBetter: true, hint: 'Created in the period' },
  { key: 'avgHoursToClearance', label: 'Request to clearance', icon: Clock, format: formatHours, higherIsBetter: false, hint: 'Average, for requests cleared in the period' },
  { key: 'slaCompliancePct', label: 'SLA compliance', icon: Gauge, format: (v) => `${v.toFixed(1)}%`, higherIsBetter: true, hint: 'Stage visits finished within their SLA' },
  { key: 'overdueRequests', label: 'Overdue requests', icon: TriangleAlert, format: formatCount, higherIsBetter: false, hint: 'Breached an SLA in the period' },
]

function Change({ kpi, higherIsBetter, format }: { kpi: Kpi; higherIsBetter: boolean; format: (v: number) => string }) {
  if (kpi.changePct == null) {
    return <span className="text-fg-muted">{kpi.previous == null ? 'No earlier data' : `Previously ${format(kpi.previous)}`}</span>
  }
  const flat = kpi.changePct === 0
  const up = kpi.changePct > 0
  const good = flat ? null : up === higherIsBetter
  const Icon = flat ? Minus : up ? ArrowUpRight : ArrowDownRight
  return (
    <span className={cn('inline-flex items-center gap-1 font-medium', good == null ? 'text-fg-muted' : good ? 'text-green-700 dark:text-green-400' : 'text-red-700 dark:text-red-400')}>
      <Icon className="size-3.5" aria-hidden="true" />
      {`${up ? '+' : ''}${kpi.changePct.toFixed(1)}%`}
      <span className="font-normal text-fg-muted">vs previous period{good == null ? '' : good ? ' (better)' : ' (worse)'}</span>
    </span>
  )
}

export function KpiStrip({ data }: { data?: ExecutiveAnalytics }) {
  return (
    <section aria-label="Key figures" className="mb-6 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5">
      {KPIS.map((def, index) => {
        const Icon = def.icon
        const kpi = data?.kpis[def.key]
        return (
          <motion.div
            key={def.key}
            initial={{ opacity: 0, y: 4 }}
            animate={{ opacity: 1, y: 0, transition: { duration: duration.base, ease: ease.out, delay: staggerDelay(index) } }}
            whileHover={{ y: -2 }}
            className="rounded-xl border border-line bg-surface p-4 shadow-sm transition-shadow duration-150 hover:shadow-md"
          >
            <div className="flex items-center justify-between gap-2">
              <h2 className="text-sm font-medium text-fg-muted">{def.label}</h2>
              <Icon className="size-5 text-fg-muted" aria-hidden="true" />
            </div>
            <p className="mt-2 text-2xl font-bold tabular-nums text-fg" title={kpi?.value != null && def.full ? def.full(kpi.value) : undefined}>
              {!kpi ? <Skeleton className="h-8 w-24" /> : kpi.value == null ? '—' : <CountUp value={kpi.value} format={def.format} />}
            </p>
            <p className="mt-1 min-h-4 text-xs">{kpi ? <Change kpi={kpi} higherIsBetter={def.higherIsBetter} format={def.format} /> : null}</p>
            <p className="mt-1 text-xs text-fg-muted">{def.hint}</p>
          </motion.div>
        )
      })}
    </section>
  )
}
