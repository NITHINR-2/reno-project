import { zodResolver } from '@hookform/resolvers/zod'
import { Save } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { Form } from '@/components/forms/Form'
import { TextareaField } from '@/components/forms/TextareaField'
import { TextField } from '@/components/forms/TextField'
import { Button } from '@/components/ui/Button'
import { useToast } from '@/components/ui/useToast'
import { formatCount, formatDate, formatDateTime, formatINR, statusLabel, todayIST } from '@/lib/format'
import type { FleetRequestDetail } from '../api'
import { useUpdateRequest } from '../queries'
import { DetailList, ReadOnlyNotice, Section } from './parts'
import { reportError } from '@/lib/reportError'

// Mirrors UpdateFleetRequestDto's Bean Validation; the server re-validates.
const schema = z.object({
  quantity: z.string().regex(/^\d+$/, 'Enter a whole number.').refine((v) => +v >= 1 && +v <= 10_000, 'Between 1 and 10,000.'),
  requiredDeposit: z.string().trim().regex(/^\d{1,13}(\.\d{1,2})?$/, 'Enter an amount in rupees, up to 2 decimals.'),
  productionEstimateWeeks: z.string().regex(/^(\d{1,3})?$/, 'Enter whole weeks.').refine((v) => v === '' || +v <= 104, 'At most 104 weeks.'),
  requestedDeliveryDate: z.string().refine((v) => v === '' || v > todayIST(), 'Must be a future date.'),
  notes: z.string().max(4000, 'At most 4000 characters.'),
})
type Values = z.infer<typeof schema>

const toValues = (r: FleetRequestDetail): Values => ({
  quantity: String(r.quantity),
  requiredDeposit: r.requiredDeposit.toFixed(2),
  productionEstimateWeeks: r.productionEstimateWeeks == null ? '' : String(r.productionEstimateWeeks),
  requestedDeliveryDate: r.requestedDeliveryDate ?? '',
  notes: r.notes ?? '',
})

export function OverviewTab({ request, canEdit }: { request: FleetRequestDetail; canEdit: boolean }) {
  return (
    <>
      <Section title="Request">
        <DetailList
          items={[
            ['Dealer', request.dealer.name],
            ['Region', request.dealer.regionName],
            ['Vehicle', `${request.variant.modelName} — ${request.variant.name}`],
            ['Unit price', formatINR(request.variant.unitPrice)],
            ['Plant', request.plant?.name],
            ['Created', `${formatDateTime(request.createdAt)}${request.createdBy ? ` by ${request.createdBy.fullName}` : ''}`],
            ['Assigned to', request.assignedTo?.fullName],
            ['Status', statusLabel(request.status)],
          ]}
        />
        {request.returnReason && <p className="mt-3 rounded-lg bg-amber-50 p-3 text-sm text-amber-900 dark:bg-amber-950 dark:text-amber-200">Returned: {request.returnReason}</p>}
        {request.cancelReason && <p className="mt-3 rounded-lg bg-red-50 p-3 text-sm text-red-800 dark:bg-red-950 dark:text-red-200">Cancelled: {request.cancelReason}</p>}
      </Section>
      <Section title="Quantities and deposit">
        {canEdit ? <EditForm request={request} /> : <ReadOnly request={request} />}
      </Section>
    </>
  )
}

function ReadOnly({ request }: { request: FleetRequestDetail }) {
  return (
    <>
      <ReadOnlyNotice>
        Details can be edited by Marketing while the request is pending marketing review.
      </ReadOnlyNotice>
      <DetailList
        items={[
          ['Quantity', formatCount(request.quantity)],
          ['From stock', formatCount(request.allocatedQty)],
          ['To produce', formatCount(request.productionRequired)],
          ['Production estimate', request.productionEstimateWeeks == null ? null : `${request.productionEstimateWeeks} weeks`],
          ['Required deposit', formatINR(request.requiredDeposit)],
          ['Deposit received', formatINR(request.depositReceived)],
          ['Requested delivery', formatDate(request.requestedDeliveryDate)],
          ['Notes', request.notes],
        ]}
      />
    </>
  )
}

function EditForm({ request }: { request: FleetRequestDetail }) {
  const toast = useToast()
  const update = useUpdateRequest(request.id)
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: toValues(request) })

  function onSubmit(values: Values) {
    update.mutate(
      {
        version: request.version,
        quantity: Number(values.quantity),
        requiredDeposit: values.requiredDeposit.trim(),
        productionEstimateWeeks: values.productionEstimateWeeks === '' ? undefined : Number(values.productionEstimateWeeks),
        requestedDeliveryDate: values.requestedDeliveryDate || undefined,
        notes: values.notes,
      },
      {
        onSuccess: (saved) => {
          form.reset(toValues(saved))
          toast.success('Request updated', `${saved.requestNumber} saved.`)
        },
        onError: (error) => reportError(toast, error, form),
      },
    )
  }

  return (
    <Form form={form} onSubmit={onSubmit} aria-label="Edit request" className="space-y-4">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <TextField name="quantity" label="Quantity" inputMode="numeric" required hint={`From stock ${request.allocatedQty}; the rest is produced.`} />
        <TextField name="requiredDeposit" label="Required deposit (₹)" inputMode="decimal" required hint={`Received so far ${formatINR(request.depositReceived)}`} />
        <TextField name="productionEstimateWeeks" label="Production estimate (weeks)" inputMode="numeric" />
        <TextField name="requestedDeliveryDate" label="Requested delivery" type="date" min={todayIST()} />
      </div>
      <TextareaField name="notes" label="Notes" rows={3} />
      <div className="flex justify-end">
        <Button type="submit" loading={update.isPending} disabled={!form.formState.isDirty} icon={<Save className="size-4" aria-hidden="true" />}>
          Save changes
        </Button>
      </div>
    </Form>
  )
}
