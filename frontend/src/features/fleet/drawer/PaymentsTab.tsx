import { zodResolver } from '@hookform/resolvers/zod'
import { CheckCircle2, Plus } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { Form } from '@/components/forms/Form'
import { SelectField } from '@/components/forms/SelectField'
import { TextField } from '@/components/forms/TextField'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { ErrorState } from '@/components/ui/ErrorState'
import { SkeletonRows } from '@/components/ui/Skeleton'
import { useToast } from '@/components/ui/useToast'
import { formatDate, formatINR, statusLabel, todayIST } from '@/lib/format'
import { PAYMENT_METHODS, RECEIPT_TYPES, type FleetRequestDetail, type Receipt } from '../api'
import { useApproveReceipt, useFleetPart, useRecordReceipt } from '../queries'
import { DetailList, ReadOnlyNotice, Rows, Section } from './parts'
import { reportError } from './reportError'

// Mirrors RecordReceiptRequest; uniqueness of the reference is enforced by the server (409).
const schema = z.object({
  receiptType: z.enum(RECEIPT_TYPES),
  paymentMethod: z.enum(PAYMENT_METHODS),
  transactionReference: z.string().trim().min(1, 'Enter the bank reference.').max(100).regex(/^[A-Za-z0-9][A-Za-z0-9 /_.-]*$/, 'Letters, digits, spaces and / _ . - only.'),
  amount: z.string().trim().regex(/^\d{1,13}(\.\d{1,2})?$/, 'Enter an amount in rupees, up to 2 decimals.').refine((v) => Number(v) > 0, 'Must be more than zero.'),
  receivedDate: z.string().min(1, 'Enter the date received.').refine((v) => v <= todayIST(), "Can't be in the future."),
})
type Values = z.infer<typeof schema>
const blank = (): Values => ({ receiptType: 'DEPOSIT', paymentMethod: 'NEFT', transactionReference: '', amount: '', receivedDate: todayIST() })

export function PaymentsTab({ request, canRecord, canApprove }: { request: FleetRequestDetail; canRecord: boolean; canApprove: boolean }) {
  const toast = useToast()
  const receipts = useFleetPart(request.id, 'receipts')
  const approve = useApproveReceipt()
  const [approving, setApproving] = useState<Receipt | null>(null)

  if (receipts.isPending) return <SkeletonRows rows={3} label="Loading receipts" />
  if (receipts.error) return <ErrorState title="Couldn't load receipts" error={receipts.error} onRetry={() => void receipts.refetch()} />

  return (
    <>
      {!canRecord && !canApprove && <ReadOnlyNotice>Receipts are recorded and approved by Finance during the finance stages.</ReadOnlyNotice>}
      <Section title="Deposits">
        <DetailList items={[['Required deposit', formatINR(request.requiredDeposit)], ['Approved deposits', formatINR(request.depositReceived)]]} />
      </Section>
      <Section title="Receipts">
        <Rows empty="No receipts recorded.">
          {receipts.data.content.map((r) => (
            <li key={r.id} className="flex flex-wrap items-center justify-between gap-3 px-3 py-2 text-sm">
              <div className="min-w-0">
                <div className="font-medium">
                  {formatINR(r.amount)} <span className="text-fg-muted">· {statusLabel(r.receiptType)} · {r.paymentMethod}</span>
                </div>
                <div className="truncate text-xs text-fg-muted">
                  {r.receiptNumber} · ref {r.transactionReference} · {formatDate(r.receivedDate)}
                  {r.recordedBy ? ` · by ${r.recordedBy}` : ''}
                </div>
              </div>
              <div className="flex items-center gap-2">
                <Badge tone={r.status === 'APPROVED' ? 'success' : r.status === 'REJECTED' ? 'danger' : 'warning'}>{statusLabel(r.status)}</Badge>
                {canApprove && r.status === 'RECORDED' && (
                  <Button size="sm" variant="secondary" onClick={() => setApproving(r)} icon={<CheckCircle2 className="size-4" aria-hidden="true" />}>
                    Approve
                  </Button>
                )}
              </div>
            </li>
          ))}
        </Rows>
      </Section>
      {canRecord && <RecordReceiptForm requestId={request.id} />}
      <ConfirmDialog
        open={approving !== null}
        title="Approve this receipt?"
        description={approving ? `${formatINR(approving.amount)}, reference ${approving.transactionReference}. Approval is final.` : undefined}
        confirmLabel="Approve receipt"
        loading={approve.isPending}
        onCancel={() => setApproving(null)}
        onConfirm={() =>
          approving &&
          approve.mutate(approving.id, {
            onSuccess: (r) => toast.success('Receipt approved', r.receiptNumber),
            onError: (error) => reportError(toast, error),
            onSettled: () => setApproving(null),
          })
        }
      />
    </>
  )
}

function RecordReceiptForm({ requestId }: { requestId: number }) {
  const toast = useToast()
  const record = useRecordReceipt(requestId)
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: blank() })
  const [confirming, setConfirming] = useState<Values | null>(null)

  function save(values: Values) {
    record.mutate(
      { ...values, transactionReference: values.transactionReference.trim(), amount: values.amount.trim() },
      {
        onSuccess: (r) => {
          form.reset(blank())
          toast.success('Receipt recorded', `${r.receiptNumber} awaits approval.`)
        },
        onError: (error) => reportError(toast, error, form),
        onSettled: () => setConfirming(null),
      },
    )
  }

  return (
    <Section title="Record a receipt">
      <Form
        form={form}
        aria-label="Record a receipt"
        className="space-y-4"
        onSubmit={setConfirming}
      >
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <SelectField name="receiptType" label="Type" required options={RECEIPT_TYPES.map((t) => ({ value: t, label: statusLabel(t) }))} />
          <SelectField name="paymentMethod" label="Method" required options={PAYMENT_METHODS.map((m) => ({ value: m, label: m.replace('_', ' ') }))} />
          <TextField name="transactionReference" label="Transaction reference" required autoComplete="off" />
          <TextField name="amount" label="Amount (₹)" inputMode="decimal" required />
          <TextField name="receivedDate" label="Received on" type="date" max={todayIST()} required />
        </div>
        <div className="flex justify-end">
          <Button type="submit" loading={record.isPending} icon={<Plus className="size-4" aria-hidden="true" />}>
            Record receipt
          </Button>
        </div>
      </Form>
      <ConfirmDialog
        open={confirming !== null}
        title="Record this receipt?"
        description={
          confirming
            ? `${statusLabel(confirming.receiptType)} of ${formatINR(Number(confirming.amount))} by ${confirming.paymentMethod}, reference ${confirming.transactionReference.trim()}, received ${formatDate(confirming.receivedDate)}. Receipts can't be deleted; a reference can only be used once.`
            : undefined
        }
        confirmLabel="Record receipt"
        loading={record.isPending}
        onCancel={() => setConfirming(null)}
        onConfirm={() => confirming && save(confirming)}
      />
    </Section>
  )
}
