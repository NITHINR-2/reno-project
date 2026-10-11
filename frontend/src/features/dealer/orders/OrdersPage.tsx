import { ShoppingBag } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { inputClass } from '@/components/forms/Field'
import { PageHeader } from '@/components/layout/PageHeader'
import { DataTable, type Column } from '@/components/tables/DataTable'
import { Card } from '@/components/ui/Card'
import { FilterBar, Labeled } from '@/features/admin/FilterBar'
import { formatCount, formatDateTime } from '@/lib/format'
import type { OrderQuery, OrderSummary } from '../api'
import { useDealerOrders } from '../queries'
import { DealerStatusBadge, ExpectedChip } from '../status'

const columns: Column<OrderSummary>[] = [
  { key: 'number', header: 'Order', cell: (o) => <span className="font-mono font-medium">{o.orderNumber}</span> },
  { key: 'status', header: 'Status', cell: (o) => <DealerStatusBadge status={o.status} /> },
  { key: 'placed', header: 'Placed', cell: (o) => formatDateTime(o.createdAt) },
  { key: 'lines', header: 'Lines', cell: (o) => o.lineCount, mobile: false },
  { key: 'units', header: 'Units', cell: (o) => formatCount(o.totalQuantity) },
  { key: 'requested', header: 'Delivery', cell: (o) => <ExpectedChip date={o.requestedDeliveryDate} status={o.status} /> },
]

export function OrdersPage() {
  const navigate = useNavigate()
  const [query, setQuery] = useState<OrderQuery>({ page: 0 })
  const orders = useDealerOrders(query)
  return (
    <>
      <PageHeader title="My Orders" description="Every order you placed, newest first. An order shows its least advanced line." />
      <Card>
        <FilterBar>
          <Labeled label="Search orders">
            {(id) => (
              <input id={id} type="search" placeholder="Order number" value={query.q ?? ''} onChange={(e) => setQuery({ q: e.target.value, page: 0 })} className={inputClass} />
            )}
          </Labeled>
        </FilterBar>
        <DataTable
          label="Orders"
          columns={columns}
          data={orders.data}
          getRowId={(o) => o.id}
          isLoading={orders.isPending}
          isFetching={orders.isFetching && !orders.isPending}
          error={orders.error}
          onRetry={() => void orders.refetch()}
          onPageChange={(page) => setQuery((q) => ({ ...q, page }))}
          onRowClick={(o) => navigate(`/dealer/orders/${o.id}`)}
          emptyTitle="No orders yet"
          emptyDescription={
            <span className="inline-flex items-center gap-1">
              <ShoppingBag className="size-4" aria-hidden="true" />
              Browse the <Link to="/dealer/catalogue" className="font-medium text-fg underline">catalogue</Link> to place one.
            </span>
          }
        />
      </Card>
    </>
  )
}
