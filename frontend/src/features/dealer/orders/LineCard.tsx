import { FileDown, XCircle } from 'lucide-react'
import { useState } from 'react'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { ErrorState } from '@/components/ui/ErrorState'
import { SkeletonRows } from '@/components/ui/Skeleton'
import { useToast } from '@/components/ui/useToast'
import { formatCount, formatDate, formatINR, statusLabel } from '@/lib/format'
import { reportError } from '@/lib/reportError'
import type { DealerQuotation, DealerReceipt, DealerRequest } from '../api'
import { useCancelRequest, useDealerQuotationPdf, useDealerRequest } from '../queries'
import { DealerStatusBadge } from '../status'
import { PaymentProofForm } from './PaymentProofForm'
import { StatusTimeline } from './StatusTimeline'

/** One order line (= one HQ fleet request): its progress, quotation, payments and the actions open to the dealer. */
export function LineCard({ line }: { line: DealerRequest }) {
  const detail = useDealerRequest(line.id)
  const current = detail.data?.request ?? line
  const title = `${current.modelName} — ${current.variantName}`
  return (
    <Card>
      <CardHeader
        title={title}
        description={`${current.requestNumber} · ${formatCount(current.quantity)} units${current.colour ? ` · ${current.colour}` : ''}`}
        actions={<DealerStatusBadge status={current.status} />}
      />
      <CardBody className="space-y-6">
        <StatusTimeline status={current.status} label={`Progress of ${current.requestNumber}`} />
        {detail.error && !detail.data ? (
          <ErrorState title="Couldn't load this line" error={detail.error} onRetry={() => void detail.refetch()} />
        ) : !detail.data ? (
          <SkeletonRows rows={3} label={`Loading ${current.requestNumber}`} />
        ) : (
          <>
            {detail.data.quotation && <QuotationPanel requestId={current.id} quotation={detail.data.quotation} />}
            {detail.data.receipts.length > 0 && <Receipts receipts={detail.data.receipts} />}
            {current.canUploadPaymentProof && (
              <section aria-labelledby={`proof-${current.id}`}>
                <h3 id={`proof-${current.id}`} className="mb-3 text-sm font-semibold text-fg">Upload payment proof</h3>
                <PaymentProofForm requestId={current.id} label={`Payment proof for ${current.requestNumber}`} />
              </section>
            )}
            {current.canCancel && <CancelLine line={current} />}
          </>
        )}
      </CardBody>
    </Card>
  )
}

function QuotationPanel({ requestId, quotation }: { requestId: number; quotation: DealerQuotation }) {
  const toast = useToast()
  const pdf = useDealerQuotationPdf()
  const rows: [string, string][] = [
    ['Base amount', formatINR(quotation.baseAmount)],
    [`GST @ ${(quotation.gstRate * 100).toFixed(quotation.gstRate * 100 % 1 ? 2 : 0)}%`, formatINR(quotation.gstAmount)],
    ['Total', formatINR(quotation.totalAmount)],
    ['Deposit required', formatINR(quotation.requiredDeposit)],
    ['Deposit received', formatINR(quotation.depositReceived)],
    ['Deposit shortfall', formatINR(quotation.depositShortfall)],
  ]
  return (
    <section aria-labelledby={`quotation-${requestId}`} className="rounded-lg border border-line p-4">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <div>
          <h3 id={`quotation-${requestId}`} className="text-sm font-semibold text-fg">Quotation {quotation.quotationNumber}</h3>
          <p className="text-xs text-fg-muted">
            Issued {formatDate(quotation.issueDate)} · valid until {formatDate(quotation.validUntil)}
          </p>
        </div>
        <Button
          size="sm"
          variant="secondary"
          loading={pdf.isPending}
          icon={<FileDown className="size-4" aria-hidden="true" />}
          onClick={() =>
            pdf.mutate(requestId, {
              onSuccess: (fileName) => toast.success('PDF downloaded', fileName),
              onError: (error) => reportError(toast, error),
            })
          }
        >
          Download PDF
        </Button>
      </div>
      <dl className="grid grid-cols-1 gap-x-6 gap-y-1 text-sm sm:grid-cols-2">
        {rows.map(([label, value]) => (
          <div key={label} className="flex justify-between gap-3">
            <dt className="text-fg-muted">{label}</dt>
            <dd className="font-medium text-fg">{value}</dd>
          </div>
        ))}
      </dl>
    </section>
  )
}

const RECEIPT_TONES: Record<string, 'warning' | 'success' | 'danger'> = { RECORDED: 'warning', APPROVED: 'success', REJECTED: 'danger' }
const RECEIPT_LABELS: Record<string, string> = { RECORDED: 'Awaiting verification', APPROVED: 'Verified', REJECTED: 'Rejected' }

function Receipts({ receipts }: { receipts: DealerReceipt[] }) {
  return (
    <section>
      <h3 className="mb-2 text-sm font-semibold text-fg">Payments</h3>
      <ul className="divide-y divide-line rounded-lg border border-line text-sm">
        {receipts.map((r) => (
          <li key={r.id} className="flex flex-wrap items-center justify-between gap-2 px-3 py-2">
            <span className="min-w-0">
              <span className="font-medium text-fg">{formatINR(r.amount)}</span>{' '}
              <span className="text-fg-muted">
                {statusLabel(r.receiptType)} · {r.paymentMethod.replace('_', ' ')} · {r.transactionReference} · {formatDate(r.receivedDate)}
              </span>
            </span>
            <Badge tone={RECEIPT_TONES[r.status] ?? 'neutral'}>{RECEIPT_LABELS[r.status] ?? statusLabel(r.status)}</Badge>
          </li>
        ))}
      </ul>
    </section>
  )
}

function CancelLine({ line }: { line: DealerRequest }) {
  const toast = useToast()
  const cancel = useCancelRequest()
  const [open, setOpen] = useState(false)
  return (
    <div className="flex justify-end border-t border-line pt-4">
      <Button variant="secondary" icon={<XCircle className="size-4" aria-hidden="true" />} onClick={() => setOpen(true)}>
        Cancel this line
      </Button>
      <ConfirmDialog
        open={open}
        title={`Cancel ${line.requestNumber}?`}
        description="You can cancel while HQ is still reviewing it. This can't be undone."
        confirmLabel="Cancel line"
        tone="danger"
        requireReason
        loading={cancel.isPending}
        onCancel={() => setOpen(false)}
        onConfirm={(reason) =>
          cancel.mutate(
            { id: line.id, reason, version: line.version },
            {
              onSuccess: () => toast.success('Line cancelled', line.requestNumber),
              onError: (error) => reportError(toast, error),
              onSettled: () => setOpen(false),
            },
          )
        }
      />
    </div>
  )
}
