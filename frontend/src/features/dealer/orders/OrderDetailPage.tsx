import { ArrowLeft } from 'lucide-react'
import { Link, useParams } from 'react-router'
import { PageHeader } from '@/components/layout/PageHeader'
import { Card, CardBody } from '@/components/ui/Card'
import { ErrorState } from '@/components/ui/ErrorState'
import { Skeleton } from '@/components/ui/Skeleton'
import { formatDateTime } from '@/lib/format'
import { toProblem } from '@/lib/problem'
import { useDealerOrder } from '../queries'
import { DealerStatusBadge, ExpectedChip } from '../status'
import { LineCard } from './LineCard'

export function OrderDetailPage() {
  const id = Number(useParams().orderId)
  const order = useDealerOrder(id)

  if (order.error && !order.data) {
    const notFound = toProblem(order.error).status === 404
    return (
      <ErrorState
        title={notFound ? 'Order not found' : "Couldn't load this order"}
        error={notFound ? undefined : order.error}
        onRetry={notFound ? undefined : () => void order.refetch()}
      />
    )
  }
  if (!order.data) {
    return (
      <div role="status" aria-label="Loading order" className="space-y-4">
        <Skeleton className="h-10 w-64" />
        <Skeleton className="h-48 w-full rounded-xl" />
      </div>
    )
  }
  const o = order.data
  return (
    <>
      <Link to="/dealer/orders" className="mb-3 inline-flex min-h-11 items-center gap-1 text-sm text-fg-muted hover:text-fg">
        <ArrowLeft className="size-4" aria-hidden="true" /> My Orders
      </Link>
      <PageHeader title={`Order ${o.orderNumber}`} description={`Placed ${formatDateTime(o.createdAt)}`} actions={<DealerStatusBadge status={o.status} />} />
      <Card className="mb-6">
        <CardBody className="flex flex-wrap items-center gap-4 text-sm">
          <ExpectedChip date={o.requestedDeliveryDate} status={o.status} />
          {o.notes && <p className="text-fg-muted">Your notes: {o.notes}</p>}
        </CardBody>
      </Card>
      <div className="space-y-4">
        {o.lines.map((line) => (
          <LineCard key={line.id} line={line} />
        ))}
      </div>
    </>
  )
}
