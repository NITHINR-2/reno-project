import { DataTable, type Column, type SortState } from '@/components/tables/DataTable'
import { StatusBadge } from '@/components/ui/Badge'
import { formatCount, formatDate, formatDateTime } from '@/lib/format'
import type { ListParams, FleetRequestRow } from '../api'
import { useFleetList } from '../queries'

const columns: Column<FleetRequestRow>[] = [
  { key: 'number', header: 'Request', sortKey: 'requestNumber', cell: (r) => <span className="font-mono font-medium text-fg">{r.requestNumber}</span> },
  {
    key: 'dealer',
    header: 'Dealer',
    sortKey: 'dealer.name',
    cell: (r) => (
      <div className="min-w-0">
        <div className="truncate">{r.dealerName}</div>
        {r.regionName && <div className="truncate text-xs text-fg-muted">{r.regionName}</div>}
      </div>
    ),
  },
  {
    key: 'vehicle',
    header: 'Vehicle',
    cell: (r) => (
      <div className="min-w-0">
        <div className="truncate">{r.modelName}</div>
        <div className="truncate text-xs text-fg-muted">{r.variantName}</div>
      </div>
    ),
  },
  { key: 'plant', header: 'Plant', cell: (r) => r.plantName ?? '—', mobile: false },
  { key: 'qty', header: 'Qty', sortKey: 'quantity', className: 'text-right tabular-nums', cell: (r) => formatCount(r.quantity) },
  { key: 'allocated', header: 'From stock', className: 'text-right tabular-nums', cell: (r) => formatCount(r.allocatedQty), mobile: false },
  { key: 'production', header: 'To produce', sortKey: 'productionRequired', className: 'text-right tabular-nums', cell: (r) => formatCount(r.productionRequired) },
  { key: 'delivery', header: 'Delivery', sortKey: 'requestedDeliveryDate', cell: (r) => formatDate(r.requestedDeliveryDate), mobile: false },
  { key: 'status', header: 'Status', sortKey: 'status', cell: (r) => <StatusBadge status={r.status} /> },
  { key: 'updated', header: 'Updated', sortKey: 'updatedAt', cell: (r) => <span className="whitespace-nowrap text-fg-muted">{formatDateTime(r.updatedAt)}</span>, mobile: false },
]

type FleetTableProps = {
  params: ListParams
  sort: SortState
  onSortChange: (sort: SortState) => void
  onPageChange: (page: number) => void
  onOpen: (id: number) => void
}

export function FleetTable({ params, sort, onSortChange, onPageChange, onOpen }: FleetTableProps) {
  const { data, isPending, isFetching, error, refetch } = useFleetList(params)
  return (
    <DataTable
      label="Fleet requests"
      columns={columns}
      data={data}
      getRowId={(r) => r.id}
      isLoading={isPending}
      isFetching={isFetching && !isPending}
      error={error}
      onRetry={() => void refetch()}
      onPageChange={onPageChange}
      onRowClick={(r) => onOpen(r.id)}
      sort={sort}
      onSortChange={onSortChange}
      emptyTitle="No fleet requests match"
      emptyDescription="Try clearing a filter or searching for another request number."
    />
  )
}
