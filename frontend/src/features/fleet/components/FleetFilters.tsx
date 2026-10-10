import { Search, X } from 'lucide-react'
import { useEffect, useId, useState, type ReactNode } from 'react'
import { inputClass } from '@/components/forms/Field'
import type { SortState } from '@/components/tables/DataTable'
import { Button } from '@/components/ui/Button'
import { statusLabel } from '@/lib/format'
import { STATUSES, type Ref } from '../api'
import { STAGES, type FilterKey } from '../filters'
import { useReferenceData } from '../queries'

const SEARCH_DEBOUNCE_MS = 300

const SORT_OPTIONS = [
  { value: 'createdAt,desc', label: 'Newest first' },
  { value: 'createdAt,asc', label: 'Oldest first' },
  { value: 'updatedAt,desc', label: 'Recently updated' },
  { value: 'requestedDeliveryDate,asc', label: 'Delivery date' },
  { value: 'quantity,desc', label: 'Largest quantity' },
  { value: 'dealer.name,asc', label: 'Dealer A–Z' },
]

type FleetFiltersProps = {
  values: Record<FilterKey, string>
  sort: SortState
  onChange: (key: FilterKey, value: string) => void
  onSortChange: (sort: SortState) => void
  onClear: () => void
}

function Labeled({ label, children, className }: { label: string; children: (id: string) => ReactNode; className?: string }) {
  const id = useId()
  return (
    <div className={className}>
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

export function FleetFilters({ values, sort, onChange, onSortChange, onClear }: FleetFiltersProps) {
  const refs = useReferenceData()
  const [search, setSearch] = useState(values.q)
  const [syncedQ, setSyncedQ] = useState(values.q)
  // Keep the box in sync when the URL changes (back button, "Clear filters").
  if (values.q !== syncedQ) {
    setSyncedQ(values.q)
    setSearch(values.q)
  }

  useEffect(() => {
    if (search === values.q) return
    const timer = window.setTimeout(() => onChange('q', search.trim()), SEARCH_DEBOUNCE_MS)
    return () => window.clearTimeout(timer)
  }, [search, values.q, onChange])

  return (
    <div className="space-y-3 border-b border-line p-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
        <Labeled label="Search" className="flex-1">
          {(id) => (
            <div className="relative">
              <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-fg-muted" aria-hidden="true" />
              <input
                id={id}
                type="search"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Request number or dealer"
                className={`${inputClass} pl-9`}
              />
            </div>
          )}
        </Labeled>
        <Labeled label="Status" className="sm:w-60">
          {(id) => (
            <select id={id} value={values.status || 'all'} onChange={(e) => onChange('status', e.target.value)} className={inputClass}>
              <option value="all">All statuses</option>
              <optgroup label="Stages">
                {Object.entries(STAGES).map(([key, stage]) => (
                  <option key={key} value={`stage:${key}`}>
                    {stage.label}
                  </option>
                ))}
              </optgroup>
              <optgroup label="Status">
                {STATUSES.map((s) => (
                  <option key={s} value={s}>
                    {statusLabel(s)}
                  </option>
                ))}
              </optgroup>
            </select>
          )}
        </Labeled>
        <Labeled label="Sort" className="md:hidden">
          {(id) => (
            <select
              id={id}
              value={`${sort.key},${sort.direction}`}
              onChange={(e) => {
                const [key, direction] = e.target.value.split(',')
                onSortChange({ key, direction: direction === 'asc' ? 'asc' : 'desc' })
              }}
              className={inputClass}
            >
              {SORT_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          )}
        </Labeled>
      </div>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-6">
        <RefSelect label="Region" value={values.regionId} options={refs.regions.data?.content} onChange={(v) => onChange('regionId', v)} />
        <RefSelect label="Dealer" value={values.dealerId} options={refs.dealers.data?.content} onChange={(v) => onChange('dealerId', v)} />
        <RefSelect label="Model" value={values.modelId} options={refs.models.data?.content} onChange={(v) => onChange('modelId', v)} />
        <RefSelect label="Plant" value={values.plantId} options={refs.plants.data?.content} onChange={(v) => onChange('plantId', v)} />
        <Labeled label="Created from">
          {(id) => <input id={id} type="date" value={values.createdFrom} max={values.createdTo || undefined} onChange={(e) => onChange('createdFrom', e.target.value)} className={inputClass} />}
        </Labeled>
        <Labeled label="Created to">
          {(id) => <input id={id} type="date" value={values.createdTo} min={values.createdFrom || undefined} onChange={(e) => onChange('createdTo', e.target.value)} className={inputClass} />}
        </Labeled>
      </div>
      <div className="flex justify-end">
        <Button variant="ghost" size="sm" onClick={onClear} icon={<X className="size-4" aria-hidden="true" />}>
          Clear filters
        </Button>
      </div>
    </div>
  )
}
