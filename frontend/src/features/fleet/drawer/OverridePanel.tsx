import { zodResolver } from '@hookform/resolvers/zod'
import { ShieldAlert } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { Form } from '@/components/forms/Form'
import { SelectField } from '@/components/forms/SelectField'
import { TextareaField } from '@/components/forms/TextareaField'
import { TextField } from '@/components/forms/TextField'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { useToast } from '@/components/ui/useToast'
import { formatDate, formatINR } from '@/lib/format'
import { reportError } from '@/lib/reportError'
import type { FleetRequestDetail, OverrideField } from '../api'
import { useOverride, useReferenceData } from '../queries'
import { OverrideTrail } from './OverrideTrail'
import { Section } from './parts'

// Mirrors the server's allow-list and parsing; anything else (status, quantities, stock, tax, receipts) is refused there.
const schema = z.object({
  requiredDeposit: z.string().trim().regex(/^\d{1,13}(\.\d{1,2})?$/, 'Enter an amount in rupees, up to 2 decimals.'),
  productionEstimateWeeks: z.string().regex(/^(\d{1,3})?$/, 'Enter whole weeks.').refine((v) => v === '' || +v <= 104, 'At most 104 weeks.'),
  requestedDeliveryDate: z.string(),
  plantId: z.string(),
  notes: z.string().max(4000, 'At most 4000 characters.'),
  reason: z.string().trim().min(1, 'A reason is required for every override.').max(1000, 'At most 1000 characters.'),
})
type Values = z.infer<typeof schema>
type Change = { field: OverrideField; label: string; from: string; to: string; value: string | null }

const LABELS: Record<OverrideField, string> = {
  requiredDeposit: 'Required deposit',
  productionEstimateWeeks: 'Production estimate',
  requestedDeliveryDate: 'Requested delivery',
  plantId: 'Plant',
  notes: 'Notes',
}

const current = (r: FleetRequestDetail): Omit<Values, 'reason'> => ({
  requiredDeposit: r.requiredDeposit.toFixed(2),
  productionEstimateWeeks: r.productionEstimateWeeks == null ? '' : String(r.productionEstimateWeeks),
  requestedDeliveryDate: r.requestedDeliveryDate ?? '',
  plantId: r.plant ? String(r.plant.id) : '',
  notes: r.notes ?? '',
})

export function OverridePanel({ request }: { request: FleetRequestDetail }) {
  const toast = useToast()
  const override = useOverride(request.id)
  const plants = useReferenceData().plants.data?.content ?? []
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { ...current(request), reason: '' } })
  const [pending, setPending] = useState<{ changes: Change[]; reason: string } | null>(null)

  const plantName = (id: string) => plants.find((p) => String(p.id) === id)?.name ?? (id ? `#${id}` : '—')
  const show: Record<OverrideField, (v: string) => string> = {
    requiredDeposit: (v) => formatINR(Number(v)),
    productionEstimateWeeks: (v) => (v ? `${v} weeks` : '—'),
    requestedDeliveryDate: (v) => formatDate(v || null),
    plantId: plantName,
    notes: (v) => v || '—',
  }

  function review(values: Values) {
    const before = current(request)
    const changes: Change[] = (Object.keys(LABELS) as OverrideField[])
      .filter((f) => (f === 'requiredDeposit' ? Number(values[f]) !== Number(before[f]) : values[f].trim() !== before[f].trim()))
      .map((f) => ({ field: f, label: LABELS[f], from: show[f](before[f]), to: show[f](values[f].trim()), value: values[f].trim() === '' ? null : values[f].trim() }))
    if (changes.length === 0) {
      form.setError('root', { message: 'Change at least one field to override.' })
      return
    }
    setPending({ changes, reason: values.reason.trim() })
  }

  function submit() {
    if (!pending) return
    override.mutate(
      { reason: pending.reason, version: request.version, changes: Object.fromEntries(pending.changes.map((c) => [c.field, c.value])) },
      {
        onSuccess: (saved) => {
          form.reset({ ...current(saved), reason: '' })
          toast.success('Override applied', `${pending.changes.length} field(s) changed on ${saved.requestNumber}; audited.`)
        },
        onError: (e) => reportError(toast, e, form),
        onSettled: () => setPending(null),
      },
    )
  }

  return (
    <>
      <Section title="Override fields">
        <Form form={form} onSubmit={review} aria-label="Supervisor override" className="space-y-4">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <TextField name="requiredDeposit" label="Required deposit (₹)" inputMode="decimal" required />
            <TextField name="productionEstimateWeeks" label="Production estimate (weeks)" inputMode="numeric" />
            <TextField name="requestedDeliveryDate" label="Requested delivery" type="date" />
            <SelectField name="plantId" label="Plant" placeholder="No plant" options={plants.map((p) => ({ value: String(p.id), label: p.name }))} />
          </div>
          <TextareaField name="notes" label="Notes" rows={2} />
          <TextareaField name="reason" label="Reason for override" rows={3} required hint="Recorded in the audit log with the old and new values." />
          {form.formState.errors.root && (
            <p role="alert" className="text-sm text-red-600 dark:text-red-400">
              {form.formState.errors.root.message}
            </p>
          )}
          <div className="flex justify-end">
            <Button type="submit" variant="danger" icon={<ShieldAlert className="size-4" aria-hidden="true" />}>
              Review override
            </Button>
          </div>
        </Form>
      </Section>
      <OverrideTrail requestId={request.id} />
      <ConfirmDialog
        open={pending !== null}
        title="Apply this override?"
        tone="danger"
        confirmLabel="Apply override"
        loading={override.isPending}
        onCancel={() => setPending(null)}
        onConfirm={submit}
        description={
          pending && (
            <span className="block space-y-3">
              <span className="block overflow-x-auto">
                <table className="w-full text-left text-sm" aria-label="Override changes">
                  <thead className="text-xs uppercase text-fg-muted">
                    <tr>
                      <th scope="col" className="py-1 pr-3">Field</th>
                      <th scope="col" className="py-1 pr-3">Current</th>
                      <th scope="col" className="py-1">New</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-line">
                    {pending.changes.map((c) => (
                      <tr key={c.field}>
                        <th scope="row" className="py-1 pr-3 font-medium text-fg">{c.label}</th>
                        <td className="py-1 pr-3 text-fg-muted line-through">{c.from}</td>
                        <td className="py-1 font-medium text-fg">{c.to}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </span>
              <span className="block">Reason: “{pending.reason}”</span>
            </span>
          )
        }
      />
    </>
  )
}
