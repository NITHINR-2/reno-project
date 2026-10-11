import { PackageCheck } from 'lucide-react'
import { useState } from 'react'
import { inputClass } from '@/components/forms/Field'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ErrorState } from '@/components/ui/ErrorState'
import { SkeletonRows } from '@/components/ui/Skeleton'
import { useToast } from '@/components/ui/useToast'
import { formatCount, formatDateTime } from '@/lib/format'
import type { FleetRequestDetail, StockOption } from '../api'
import { useFleetPart, useMapStock } from '../queries'
import { DetailList, ReadOnlyNotice, Rows, Section } from './parts'
import { reportError } from '@/lib/reportError'

export function InventoryTab({ request, canEdit }: { request: FleetRequestDetail; canEdit: boolean }) {
  const active = request.allocations.filter((a) => a.status === 'ACTIVE')
  return (
    <>
      <Section title="Allocation">
        <DetailList
          items={[
            ['Requested', formatCount(request.quantity)],
            ['From stock', formatCount(request.allocatedQty)],
            ['To produce', formatCount(request.productionRequired)],
            ['Eligible stock available', formatCount(request.eligibleStockAvailable)],
            ['Stock mapped', request.stockMappedAt ? formatDateTime(request.stockMappedAt) : 'Not yet'],
          ]}
        />
      </Section>
      {canEdit ? (
        <StockMapper request={request} />
      ) : (
        <Section title="Allocated stock">
          <ReadOnlyNotice>Stock can be mapped by Marketing while the request is pending marketing review.</ReadOnlyNotice>
          <Rows empty="No stock allocated.">
            {request.allocations.map((a) => (
              <li key={a.id} className="flex items-center justify-between gap-3 px-3 py-2 text-sm">
                <span>
                  {a.locationName} <span className="text-fg-muted">({a.locationCode})</span>
                </span>
                <span className="flex items-center gap-2 tabular-nums">
                  {formatCount(a.quantity)}
                  <Badge tone={a.status === 'ACTIVE' ? 'success' : 'neutral'}>{a.status.toLowerCase()}</Badge>
                </span>
              </li>
            ))}
          </Rows>
          {active.length === 0 && request.allocations.length > 0 && <p className="mt-2 text-xs text-fg-muted">No active allocations.</p>}
        </Section>
      )}
    </>
  )
}

function StockMapper({ request }: { request: FleetRequestDetail }) {
  const toast = useToast()
  const options = useFleetPart(request.id, 'stockOptions')
  const mapStock = useMapStock(request.id)
  const current = new Map(request.allocations.filter((a) => a.status === 'ACTIVE').map((a) => [a.stockId, a.quantity]))
  const [draft, setDraft] = useState<Record<number, string>>(() =>
    Object.fromEntries([...current].map(([stockId, qty]) => [stockId, String(qty)])),
  )

  if (options.isPending) return <SkeletonRows rows={3} label="Loading stock" />
  if (options.error) return <ErrorState title="Couldn't load stock" error={options.error} onRetry={() => void options.refetch()} />

  // Units already allocated to this request are not in quantityAvailable, so they can be kept.
  const maxFor = (s: StockOption) => s.quantityAvailable + (current.get(s.id) ?? 0)
  // A row this request fully took has 0 available and is not an "option" any more; keep it editable.
  const listed = new Set(options.data.content.map((s) => s.id))
  const rows: StockOption[] = [
    ...options.data.content,
    ...request.allocations
      .filter((a) => a.status === 'ACTIVE' && !listed.has(a.stockId))
      .map((a) => ({
        id: a.stockId,
        locationId: a.locationId,
        locationCode: a.locationCode,
        locationName: a.locationName,
        locationType: 'ALLOCATED',
        variantName: request.variant.name,
        quantityAvailable: 0,
        quantityAllocated: a.quantity,
      })),
  ]
  const lines = rows.map((s) => ({ stockId: s.id, quantity: Number(draft[s.id] || 0), max: maxFor(s) }))
  const invalid = lines.some((l) => !Number.isInteger(l.quantity) || l.quantity < 0 || l.quantity > l.max)
  const total = lines.reduce((sum, l) => sum + (Number.isFinite(l.quantity) ? l.quantity : 0), 0)
  const tooMany = total > request.quantity

  function save() {
    mapStock.mutate(
      { version: request.version, lines: lines.filter((l) => l.quantity > 0).map(({ stockId, quantity }) => ({ stockId, quantity })) },
      {
        onSuccess: (saved) => toast.success('Stock mapped', `${saved.allocatedQty} from stock, ${saved.productionRequired} to produce.`),
        onError: (error) => reportError(toast, error),
      },
    )
  }

  return (
    <Section
      title="Map stock"
      actions={
        <Button size="sm" onClick={save} loading={mapStock.isPending} disabled={invalid || tooMany} icon={<PackageCheck className="size-4" aria-hidden="true" />}>
          Save mapping
        </Button>
      }
    >
      <Rows empty="No eligible stock for this variant. Everything will be produced.">
        {rows.map((s) => {
          const value = draft[s.id] ?? ''
          const qty = Number(value || 0)
          const bad = !Number.isInteger(qty) || qty < 0 || qty > maxFor(s)
          return (
            <li key={s.id} className="flex flex-wrap items-center justify-between gap-3 px-3 py-2 text-sm">
              <div className="min-w-0">
                <div className="truncate font-medium">{s.locationName}</div>
                <div className="text-xs text-fg-muted">
                  {s.locationCode} · {s.locationType.toLowerCase()} · up to {formatCount(maxFor(s))}
                </div>
              </div>
              <input
                aria-label={`Units from ${s.locationName}`}
                inputMode="numeric"
                value={value}
                onChange={(e) => setDraft((d) => ({ ...d, [s.id]: e.target.value.replace(/[^\d]/g, '') }))}
                aria-invalid={bad}
                className={`${inputClass} w-24 text-right`}
              />
            </li>
          )
        })}
      </Rows>
      <p className={`mt-3 text-sm ${tooMany ? 'text-red-600' : 'text-fg-muted'}`} aria-live="polite">
        {formatCount(total)} of {formatCount(request.quantity)} from stock
        {tooMany ? ' — more than requested.' : `; ${formatCount(request.quantity - total)} would be produced (the server recalculates).`}
      </p>
    </Section>
  )
}
