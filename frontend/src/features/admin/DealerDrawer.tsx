import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { Form } from '@/components/forms/Form'
import { SelectField } from '@/components/forms/SelectField'
import { TextField } from '@/components/forms/TextField'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Drawer } from '@/components/ui/Drawer'
import { useToast } from '@/components/ui/useToast'
import { statusLabel } from '@/lib/format'
import { reportError } from '@/lib/reportError'
import { DEALER_STATUSES, type Dealer, type DealerBody } from './api'
import { useCreateDealer, useUpdateDealer } from './queries'

// Mirrors DealerPatterns on the server (GSTIN also matches the database CHECK).
const optional = (re: RegExp, message: string) => z.string().trim().refine((v) => v === '' || re.test(v), message)
const schema = z.object({
  dealerCode: z.string().trim().regex(/^[A-Z0-9][A-Z0-9-]*$/, 'Upper-case letters, digits and hyphens.').max(30),
  name: z.string().trim().min(1, 'Enter the dealer name.').max(150),
  regionId: z.string().min(1, 'Choose a region.'),
  gstin: optional(/^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$/, 'Enter a valid 15-character GSTIN.'),
  contactName: z.string().trim().max(150),
  contactEmail: z.string().trim().refine((v) => v === '' || z.string().email().safeParse(v).success, 'Enter a valid email.'),
  contactPhone: optional(/^\+?[0-9 -]{7,20}$/, 'Enter a valid phone number.'),
  addressLine: z.string().trim().max(255),
  city: z.string().trim().min(1, 'Enter the city.').max(100),
  state: z.string().trim().min(1, 'Enter the state.').max(100),
  postalCode: optional(/^[1-9][0-9]{5}$/, 'Enter a 6-digit PIN code.'),
  status: z.enum(DEALER_STATUSES),
})
type Values = z.infer<typeof schema>

const toValues = (d?: Dealer): Values => ({
  dealerCode: d?.dealerCode ?? '',
  name: d?.name ?? '',
  regionId: d ? String(d.regionId) : '',
  gstin: d?.gstin ?? '',
  contactName: d?.contactName ?? '',
  contactEmail: d?.contactEmail ?? '',
  contactPhone: d?.contactPhone ?? '',
  addressLine: d?.addressLine ?? '',
  city: d?.city ?? '',
  state: d?.state ?? '',
  postalCode: d?.postalCode ?? '',
  status: d?.status ?? 'PENDING_ONBOARDING',
})

const toBody = (v: Values): DealerBody => ({
  name: v.name,
  regionId: Number(v.regionId),
  gstin: v.gstin || undefined,
  contactName: v.contactName || undefined,
  contactEmail: v.contactEmail || undefined,
  contactPhone: v.contactPhone || undefined,
  addressLine: v.addressLine || undefined,
  city: v.city,
  state: v.state,
  postalCode: v.postalCode || undefined,
})

type Props = { target: Dealer | 'new' | null; regions: { id: number; name: string }[]; onClose: () => void }

export function DealerDrawer({ target, regions, onClose }: Props) {
  const title = target === 'new' ? 'Onboard a dealer' : target ? target.name : ''
  return (
    <Drawer open={target !== null} onClose={onClose} title={title} description={target && target !== 'new' ? target.dealerCode : 'New dealers start pending onboarding.'}>
      {target !== null && <DealerForm key={target === 'new' ? 'new' : target.id} dealer={target === 'new' ? undefined : target} regions={regions} onDone={onClose} />}
    </Drawer>
  )
}

function DealerForm({ dealer, regions, onDone }: { dealer?: Dealer; regions: Props['regions']; onDone: () => void }) {
  const toast = useToast()
  const create = useCreateDealer()
  const update = useUpdateDealer()
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: toValues(dealer) })
  const [statusChange, setStatusChange] = useState<Values | null>(null)

  function save(v: Values) {
    const done = { onSuccess: () => (toast.success(dealer ? 'Dealer updated' : 'Dealer onboarded', v.name), onDone()), onError: (e: unknown) => reportError(toast, e, form) }
    if (dealer) update.mutate({ id: dealer.id, version: dealer.version, ...toBody(v), status: v.status }, { ...done, onSettled: () => setStatusChange(null) })
    else create.mutate({ dealerCode: v.dealerCode, ...toBody(v) }, done)
  }

  return (
    <>
      <Form
        form={form}
        aria-label={dealer ? 'Edit dealer' : 'Onboard dealer'}
        className="space-y-4"
        // Activating or suspending a dealer changes who can order, so it is confirmed.
        onSubmit={(v) => (dealer && v.status !== dealer.status ? setStatusChange(v) : save(v))}
      >
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <TextField name="dealerCode" label="Dealer code" required disabled={!!dealer} autoCapitalize="characters" />
          <TextField name="name" label="Name" required />
          <SelectField name="regionId" label="Region" required placeholder="Choose…" options={regions.map((r) => ({ value: String(r.id), label: r.name }))} />
          <TextField name="gstin" label="GSTIN" autoCapitalize="characters" />
          <TextField name="contactName" label="Contact name" />
          <TextField name="contactEmail" label="Contact email" type="email" />
          <TextField name="contactPhone" label="Contact phone" type="tel" />
          <TextField name="addressLine" label="Address" />
          <TextField name="city" label="City" required />
          <TextField name="state" label="State" required />
          <TextField name="postalCode" label="PIN code" inputMode="numeric" />
          {dealer && <SelectField name="status" label="Status" required options={DEALER_STATUSES.map((s) => ({ value: s, label: statusLabel(s) }))} />}
        </div>
        <div className="flex justify-end">
          <Button type="submit" loading={create.isPending || update.isPending}>
            {dealer ? 'Save changes' : 'Onboard dealer'}
          </Button>
        </div>
      </Form>
      <ConfirmDialog
        open={statusChange !== null}
        title={`Change status to ${statusChange ? statusLabel(statusChange.status).toLowerCase() : ''}?`}
        description={
          statusChange?.status === 'ACTIVE'
            ? 'The dealer can be used on new fleet requests.'
            : 'The dealer can no longer be used on new fleet requests. Existing requests are unaffected.'
        }
        confirmLabel="Change status"
        tone={statusChange?.status === 'SUSPENDED' ? 'danger' : 'primary'}
        loading={update.isPending}
        onCancel={() => setStatusChange(null)}
        onConfirm={() => statusChange && save(statusChange)}
      />
    </>
  )
}
