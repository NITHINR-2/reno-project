import { zodResolver } from '@hookform/resolvers/zod'
import { CalendarPlus, Truck } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { Form } from '@/components/forms/Form'
import { TextField } from '@/components/forms/TextField'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { ErrorState } from '@/components/ui/ErrorState'
import { SkeletonRows } from '@/components/ui/Skeleton'
import { useToast } from '@/components/ui/useToast'
import { formatCount, formatDate, statusLabel, todayIST } from '@/lib/format'
import type { FleetRequestDetail } from '../api'
import { useFleetPart, useScheduleDispatch, useScheduleProduction } from '../queries'
import { ReadOnlyNotice, Rows, Section } from './parts'
import { reportError } from './reportError'

const notPast = (v: string) => v >= todayIST()
const productionSchema = z
  .object({
    scheduledStart: z.string().min(1, 'Enter a start date.').refine(notPast, "Can't be in the past."),
    estimatedCompletion: z.string().min(1, 'Enter a completion date.'),
    notes: z.string().max(1000),
  })
  .refine((v) => v.estimatedCompletion >= v.scheduledStart, { path: ['estimatedCompletion'], message: 'Must be on or after the start.' })
const dispatchSchema = z.object({
  scheduledDate: z.string().min(1, 'Enter a dispatch date.').refine(notPast, "Can't be in the past."),
  carrier: z.string().max(150),
  vehicleReference: z.string().max(100),
  notes: z.string().max(1000),
})

type Props = { request: FleetRequestDetail; canProduction: boolean; canDispatch: boolean }

export function ScheduleTab({ request, canProduction, canDispatch }: Props) {
  const orders = useFleetPart(request.id, 'productionOrders')
  const dispatches = useFleetPart(request.id, 'dispatches')

  return (
    <>
      {!canProduction && !canDispatch && <ReadOnlyNotice>Production and dispatch are scheduled by a Supervisor once the request is financially cleared.</ReadOnlyNotice>}
      <Section title="Production orders">
        {orders.isPending ? (
          <SkeletonRows rows={2} label="Loading production orders" />
        ) : orders.error ? (
          <ErrorState title="Couldn't load production orders" error={orders.error} onRetry={() => void orders.refetch()} />
        ) : (
          <Rows empty={request.productionRequired === 0 ? 'Nothing to produce; all units come from stock.' : 'Not scheduled yet.'}>
            {orders.data.content.map((o) => (
              <li key={o.id} className="flex flex-wrap items-center justify-between gap-2 px-3 py-2 text-sm">
                <span>
                  <span className="font-mono">{o.productionNumber}</span> · {formatCount(o.quantity)} units at {o.plantName}
                  <span className="block text-xs text-fg-muted">
                    {formatDate(o.scheduledStart)} → {formatDate(o.estimatedCompletion)}
                  </span>
                </span>
                <Badge>{statusLabel(o.status)}</Badge>
              </li>
            ))}
          </Rows>
        )}
      </Section>
      {canProduction && <ProductionForm request={request} />}
      <Section title="Dispatches">
        {dispatches.isPending ? (
          <SkeletonRows rows={2} label="Loading dispatches" />
        ) : dispatches.error ? (
          <ErrorState title="Couldn't load dispatches" error={dispatches.error} onRetry={() => void dispatches.refetch()} />
        ) : (
          <Rows empty="Not scheduled yet.">
            {dispatches.data.content.map((d) => (
              <li key={d.id} className="flex flex-wrap items-center justify-between gap-2 px-3 py-2 text-sm">
                <span>
                  <span className="font-mono">{d.dispatchNumber}</span> · {formatCount(d.quantity)} units on {formatDate(d.scheduledDate)}
                  {(d.carrier || d.vehicleReference) && (
                    <span className="block text-xs text-fg-muted">{[d.carrier, d.vehicleReference].filter(Boolean).join(' · ')}</span>
                  )}
                </span>
                <Badge>{statusLabel(d.status)}</Badge>
              </li>
            ))}
          </Rows>
        )}
      </Section>
      {canDispatch && <DispatchForm request={request} />}
    </>
  )
}

function ProductionForm({ request }: { request: FleetRequestDetail }) {
  const toast = useToast()
  const schedule = useScheduleProduction(request.id)
  const form = useForm<z.infer<typeof productionSchema>>({
    resolver: zodResolver(productionSchema),
    defaultValues: { scheduledStart: todayIST(), estimatedCompletion: '', notes: '' },
  })
  const [confirming, setConfirming] = useState<z.infer<typeof productionSchema> | null>(null)
  return (
    <Section title={`Schedule production of ${formatCount(request.productionRequired)} units`}>
      <Form
        form={form}
        aria-label="Schedule production"
        className="space-y-4"
        onSubmit={setConfirming}
      >
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <TextField name="scheduledStart" label="Start" type="date" min={todayIST()} required />
          <TextField name="estimatedCompletion" label="Estimated completion" type="date" min={todayIST()} required />
        </div>
        <TextField name="notes" label="Notes" />
        <div className="flex justify-end">
          <Button type="submit" loading={schedule.isPending} icon={<CalendarPlus className="size-4" aria-hidden="true" />}>
            Schedule production
          </Button>
        </div>
      </Form>
      <ConfirmDialog
        open={confirming !== null}
        title="Schedule production?"
        description={
          confirming
            ? `${formatCount(request.productionRequired)} units from ${formatDate(confirming.scheduledStart)} to ${formatDate(confirming.estimatedCompletion)}. The request moves to production scheduled.`
            : undefined
        }
        confirmLabel="Schedule production"
        loading={schedule.isPending}
        onCancel={() => setConfirming(null)}
        onConfirm={() =>
          confirming &&
          schedule.mutate(
            { ...confirming, notes: confirming.notes || undefined, version: request.version },
            {
              onSuccess: () => toast.success('Production scheduled', `${request.requestNumber} is now production scheduled.`),
              onError: (e) => reportError(toast, e, form),
              onSettled: () => setConfirming(null),
            },
          )
        }
      />
    </Section>
  )
}

function DispatchForm({ request }: { request: FleetRequestDetail }) {
  const toast = useToast()
  const schedule = useScheduleDispatch(request.id)
  const form = useForm<z.infer<typeof dispatchSchema>>({
    resolver: zodResolver(dispatchSchema),
    defaultValues: { scheduledDate: '', carrier: '', vehicleReference: '', notes: '' },
  })
  const [confirming, setConfirming] = useState<z.infer<typeof dispatchSchema> | null>(null)
  return (
    <Section title={`Schedule dispatch of ${formatCount(request.quantity)} units`}>
      <Form
        form={form}
        aria-label="Schedule dispatch"
        className="space-y-4"
        onSubmit={setConfirming}
      >
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <TextField name="scheduledDate" label="Dispatch date" type="date" min={todayIST()} required />
          <TextField name="carrier" label="Carrier" />
          <TextField name="vehicleReference" label="Vehicle reference" />
          <TextField name="notes" label="Notes" />
        </div>
        <div className="flex justify-end">
          <Button type="submit" loading={schedule.isPending} icon={<Truck className="size-4" aria-hidden="true" />}>
            Schedule dispatch
          </Button>
        </div>
      </Form>
      <ConfirmDialog
        open={confirming !== null}
        title="Schedule dispatch?"
        description={confirming ? `${formatCount(request.quantity)} units on ${formatDate(confirming.scheduledDate)}. The request moves to dispatch scheduled.` : undefined}
        confirmLabel="Schedule dispatch"
        loading={schedule.isPending}
        onCancel={() => setConfirming(null)}
        onConfirm={() =>
          confirming &&
          schedule.mutate(
            {
              scheduledDate: confirming.scheduledDate,
              carrier: confirming.carrier || undefined,
              vehicleReference: confirming.vehicleReference || undefined,
              notes: confirming.notes || undefined,
              version: request.version,
            },
            {
              onSuccess: () => toast.success('Dispatch scheduled', `${request.requestNumber} is now dispatch scheduled.`),
              onError: (e) => reportError(toast, e, form),
              onSettled: () => setConfirming(null),
            },
          )
        }
      />
    </Section>
  )
}
