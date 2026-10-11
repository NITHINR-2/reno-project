import { Building2 } from 'lucide-react'
import { useState } from 'react'
import { inputClass } from '@/components/forms/Field'
import { DataTable, type Column } from '@/components/tables/DataTable'
import { Badge, type Tone } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { formatDateTime, statusLabel } from '@/lib/format'
import { DEALER_STATUSES, type Dealer, type DealerQuery, type DealerStatus } from './api'
import { DealerDrawer } from './DealerDrawer'
import { FilterBar, Labeled } from './FilterBar'
import { useDealers, useRegions } from './queries'

const DEALER_TONE: Record<DealerStatus, Tone> = { PENDING_ONBOARDING: 'warning', ACTIVE: 'success', SUSPENDED: 'danger' }

const columns: Column<Dealer>[] = [
  { key: 'code', header: 'Code', cell: (d) => <span className="font-mono">{d.dealerCode}</span> },
  { key: 'name', header: 'Dealer', cell: (d) => d.name },
  { key: 'region', header: 'Region', cell: (d) => d.regionName ?? '—' },
  { key: 'city', header: 'City', cell: (d) => `${d.city}, ${d.state}`, mobile: false },
  { key: 'status', header: 'Status', cell: (d) => <Badge tone={DEALER_TONE[d.status]}>{statusLabel(d.status)}</Badge> },
  { key: 'onboarded', header: 'Onboarded', cell: (d) => formatDateTime(d.onboardedAt), mobile: false },
]

export function DealersTab() {
  const [query, setQuery] = useState<DealerQuery>({ page: 0 })
  const [editing, setEditing] = useState<Dealer | 'new' | null>(null)
  const dealers = useDealers(query)
  const regions = useRegions()
  const set = (patch: Partial<DealerQuery>) => setQuery((q) => ({ ...q, ...patch, page: 0 }))

  return (
    <>
      <FilterBar
        actions={
          <Button onClick={() => setEditing('new')} icon={<Building2 className="size-4" aria-hidden="true" />}>
            Onboard dealer
          </Button>
        }
      >
        <Labeled label="Search dealers">
          {(id) => <input id={id} type="search" placeholder="Name or code" value={query.q ?? ''} onChange={(e) => set({ q: e.target.value })} className={inputClass} />}
        </Labeled>
        <Labeled label="Region">
          {(id) => (
            <select id={id} value={query.regionId ?? ''} onChange={(e) => set({ regionId: e.target.value })} className={inputClass}>
              <option value="">All regions</option>
              {regions.data?.content.map((r) => (
                <option key={r.id} value={String(r.id)}>
                  {r.name}
                </option>
              ))}
            </select>
          )}
        </Labeled>
        <Labeled label="Status">
          {(id) => (
            <select id={id} value={query.status ?? ''} onChange={(e) => set({ status: e.target.value })} className={inputClass}>
              <option value="">All</option>
              {DEALER_STATUSES.map((s) => (
                <option key={s} value={s}>
                  {statusLabel(s)}
                </option>
              ))}
            </select>
          )}
        </Labeled>
      </FilterBar>
      <DataTable
        label="Dealers"
        columns={columns}
        data={dealers.data}
        getRowId={(d) => d.id}
        isLoading={dealers.isPending}
        isFetching={dealers.isFetching && !dealers.isPending}
        error={dealers.error}
        onRetry={() => void dealers.refetch()}
        onPageChange={(page) => setQuery((q) => ({ ...q, page }))}
        onRowClick={setEditing}
        emptyTitle="No dealers match"
      />
      <DealerDrawer target={editing} regions={regions.data?.content ?? []} onClose={() => setEditing(null)} />
    </>
  )
}
