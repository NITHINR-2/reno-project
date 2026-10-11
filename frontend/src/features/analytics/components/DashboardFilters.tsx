import { useId, type ReactNode } from 'react'
import { inputClass } from '@/components/forms/Field'
import { cn } from '@/lib/cn'
import { useReferenceData } from '@/features/fleet/queries'
import type { Ref } from '@/features/fleet/api'
import { RANGES, type Range, type useDashboardFilters } from '../filters'

function Labeled({ label, children }: { label: string; children: (id: string) => ReactNode }) {
  const id = useId()
  return (
    <div className="min-w-0">
      <label htmlFor={id} className="mb-1 block text-xs font-medium text-fg-muted">
        {label}
      </label>
      {children(id)}
    </div>
  )
}

function RefSelect({ label, value, options, onChange }: { label: string; value: string; options?: Ref[]; onChange: (v: string) => void }) {
  return (
    <Labeled label={label}>
      {(id) => (
        <select id={id} value={value} onChange={(e) => onChange(e.target.value)} className={inputClass}>
          <option value="">All</option>
          {options?.map((o) => (
            <option key={o.id} value={String(o.id)}>
              {o.name}
            </option>
          ))}
        </select>
      )}
    </Labeled>
  )
}

/** One row of filters above the charts; every change is written to the URL. */
export function DashboardFilters({ filters }: { filters: ReturnType<typeof useDashboardFilters> }) {
  const refs = useReferenceData()
  const { range, region, plant, customFrom, customTo, error, setRange, update } = filters
  return (
    <section aria-label="Filters" className="mb-6 rounded-xl border border-line bg-surface p-4 shadow-sm">
      <div className="flex flex-wrap items-end gap-3">
        <div>
          <span id="range-label" className="mb-1 block text-xs font-medium text-fg-muted">
            Period
          </span>
          <div role="group" aria-labelledby="range-label" className="inline-flex flex-wrap rounded-lg border border-line p-0.5">
            {(Object.keys(RANGES) as Range[]).map((r) => (
              <button
                key={r}
                type="button"
                aria-pressed={range === r}
                onClick={() => setRange(r)}
                className={cn(
                  'min-h-11 rounded-md px-3 text-sm font-medium transition-colors duration-150 focus-visible:outline-2 focus-visible:outline-accent sm:min-h-9',
                  range === r ? 'bg-accent text-charcoal' : 'text-fg-muted hover:bg-surface-muted',
                )}
              >
                {RANGES[r].label}
              </button>
            ))}
          </div>
        </div>
        {range === 'custom' && (
          <>
            <Labeled label="From">
              {(id) => <input id={id} type="date" value={customFrom} max={customTo || undefined} onChange={(e) => update({ from: e.target.value })} className={inputClass} />}
            </Labeled>
            <Labeled label="To">
              {(id) => <input id={id} type="date" value={customTo} min={customFrom || undefined} onChange={(e) => update({ to: e.target.value })} className={inputClass} />}
            </Labeled>
          </>
        )}
        <div className="grid w-full min-w-0 grid-cols-1 gap-3 sm:w-auto sm:flex-1 sm:grid-cols-2 sm:min-w-80">
          <RefSelect label="Region" value={region} options={refs.regions.data?.content} onChange={(v) => update({ region: v })} />
          <RefSelect label="Plant" value={plant} options={refs.plants.data?.content} onChange={(v) => update({ plant: v })} />
        </div>
      </div>
      {error && (
        <p role="alert" className="mt-2 text-sm text-red-700 dark:text-red-400">
          {error}
        </p>
      )}
    </section>
  )
}
