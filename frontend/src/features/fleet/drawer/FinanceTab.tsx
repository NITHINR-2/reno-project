import { Calculator, FileCheck2 } from 'lucide-react'
import { useState } from 'react'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { ErrorState } from '@/components/ui/ErrorState'
import { SkeletonRows } from '@/components/ui/Skeleton'
import { useToast } from '@/components/ui/useToast'
import { formatCount, formatDate, formatINR, statusLabel } from '@/lib/format'
import type { FleetRequestDetail, Quotation } from '../api'
import { useCalculateQuotation, useFleetPart, useIssueQuotation } from '../queries'
import { DetailList, ReadOnlyNotice, Rows, Section } from './parts'
import { reportError } from '@/lib/reportError'

const STATUS_TONE = { ISSUED: 'success', DRAFT: 'accent', CANCELLED: 'neutral', SUPERSEDED: 'neutral' } as const

export function FinanceTab({ request, canEdit }: { request: FleetRequestDetail; canEdit: boolean }) {
  const toast = useToast()
  const quotations = useFleetPart(request.id, 'quotations')
  const calculate = useCalculateQuotation(request.id)
  const issue = useIssueQuotation()
  const [confirmIssue, setConfirmIssue] = useState(false)

  if (quotations.isPending) return <SkeletonRows rows={4} label="Loading quotations" />
  if (quotations.error) return <ErrorState title="Couldn't load quotations" error={quotations.error} onRetry={() => void quotations.refetch()} />

  const list = quotations.data.content
  const current = list.find((q) => q.status === 'ISSUED') ?? list.find((q) => q.status === 'DRAFT')
  const draft = current?.status === 'DRAFT' ? current : undefined

  return (
    <>
      {!canEdit && <ReadOnlyNotice>Quotations are calculated and issued by Finance while the request is pending finance clearance.</ReadOnlyNotice>}
      <Section
        title={current ? `Quotation ${current.quotationNumber}` : 'Quotation'}
        actions={
          canEdit && (
            <div className="flex flex-wrap gap-2">
              <Button
                size="sm"
                variant="secondary"
                loading={calculate.isPending}
                onClick={() =>
                  calculate.mutate(undefined, {
                    onSuccess: (q) => toast.success('Quotation calculated', `${q.quotationNumber} is a draft; review it, then issue.`),
                    onError: (error) => reportError(toast, error),
                  })
                }
                icon={<Calculator className="size-4" aria-hidden="true" />}
              >
                {draft ? 'Recalculate' : 'Calculate'}
              </Button>
              {draft && (
                <Button size="sm" onClick={() => setConfirmIssue(true)} icon={<FileCheck2 className="size-4" aria-hidden="true" />}>
                  Issue
                </Button>
              )}
            </div>
          )
        }
      >
        {current ? <Breakdown quotation={current} /> : <p className="text-sm text-fg-muted">No quotation yet.</p>}
      </Section>
      {list.length > 1 && (
        <Section title="History">
          <Rows empty="">
            {list.map((q) => (
              <li key={q.id} className="flex items-center justify-between gap-3 px-3 py-2 text-sm">
                <span className="font-mono">{q.quotationNumber}</span>
                <span className="flex items-center gap-2">
                  {formatINR(q.totalAmount)}
                  <Badge tone={STATUS_TONE[q.status as keyof typeof STATUS_TONE] ?? 'neutral'}>{statusLabel(q.status)}</Badge>
                </span>
              </li>
            ))}
          </Rows>
        </Section>
      )}
      <ConfirmDialog
        open={confirmIssue && !!draft}
        title={`Issue ${draft?.quotationNumber ?? 'quotation'}?`}
        description="The dealer is quoted these figures. Issuing is not payment: the request then waits for deposits to be recorded and approved."
        confirmLabel="Issue quotation"
        loading={issue.isPending}
        onCancel={() => setConfirmIssue(false)}
        onConfirm={() =>
          draft &&
          issue.mutate(
            { quotationId: draft.id, version: draft.version },
            {
              onSuccess: (q) => toast.success('Quotation issued', `${q.quotationNumber} valid until ${formatDate(q.validUntil)}.`),
              onError: (error) => reportError(toast, error),
              onSettled: () => setConfirmIssue(false),
            },
          )
        }
      />
    </>
  )
}

function Breakdown({ quotation: q }: { quotation: Quotation }) {
  return (
    <>
      <div className="mb-3">
        <Badge tone={STATUS_TONE[q.status as keyof typeof STATUS_TONE] ?? 'neutral'}>{statusLabel(q.status)}</Badge>
        {q.paymentStatus && q.status === 'ISSUED' && <Badge className="ml-2">{statusLabel(q.paymentStatus)}</Badge>}
      </div>
      <DetailList
        items={[
          ['Base amount', formatINR(q.baseAmount)],
          ['GST rate', percent(q.gstRate)],
          ['GST amount', formatINR(q.gstAmount)],
          ['Total', <strong key="t">{formatINR(q.totalAmount)}</strong>],
          ['Required deposit', formatINR(q.requiredDeposit)],
          ['Deposit received', formatINR(q.depositReceived)],
          ['Deposit shortfall', formatINR(q.depositShortfall)],
          ['Issued', q.issueDate ? `${formatDate(q.issueDate)}${q.issuedBy ? ` by ${q.issuedBy}` : ''}` : 'Not issued'],
          ['Valid until', formatDate(q.validUntil)],
        ]}
      />
      {q.lines.length > 0 && <LineItems quotation={q} />}
      {q.termsText && (
        <p className="mt-4 whitespace-pre-line rounded-lg bg-surface-muted p-3 text-xs text-fg-muted">
          <span className="font-semibold">Terms: </span>
          {q.termsText}
        </p>
      )}
      <p className="mt-3 text-xs text-fg-muted">All figures are calculated by the server (per line, rounded half-up to the paisa, then summed).</p>
    </>
  )
}

const percent = (rate: number) => `${(rate * 100).toFixed(2).replace(/\.00$/, '')}%`

function LineItems({ quotation: q }: { quotation: Quotation }) {
  return (
    <div className="mt-4 overflow-x-auto rounded-lg border border-line">
      <table className="w-full min-w-[520px] text-sm" aria-label="Quotation lines">
        <thead className="bg-surface-muted text-xs uppercase tracking-wide text-fg-muted">
          <tr>
            <th scope="col" className="px-3 py-2 text-left">Item</th>
            <th scope="col" className="px-3 py-2 text-right">Qty</th>
            <th scope="col" className="px-3 py-2 text-right">Unit price</th>
            <th scope="col" className="px-3 py-2 text-right">Base</th>
            <th scope="col" className="px-3 py-2 text-right">GST</th>
            <th scope="col" className="px-3 py-2 text-right">Line total</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-line tabular-nums">
          {q.lines.map((line) => (
            <tr key={line.lineNo}>
              <td className="px-3 py-2">{line.description}</td>
              <td className="px-3 py-2 text-right">{formatCount(line.quantity)}</td>
              <td className="px-3 py-2 text-right">{formatINR(line.unitPrice)}</td>
              <td className="px-3 py-2 text-right">{formatINR(line.baseAmount)}</td>
              <td className="px-3 py-2 text-right">
                {formatINR(line.gstAmount)} <span className="text-xs text-fg-muted">({percent(line.gstRate)})</span>
              </td>
              <td className="px-3 py-2 text-right font-medium">{formatINR(line.lineTotal)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
