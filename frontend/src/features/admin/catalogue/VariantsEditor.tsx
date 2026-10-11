import { zodResolver } from '@hookform/resolvers/zod'
import { Plus } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { inputClass } from '@/components/forms/Field'
import { Form } from '@/components/forms/Form'
import { SelectField } from '@/components/forms/SelectField'
import { TextField } from '@/components/forms/TextField'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { useToast } from '@/components/ui/useToast'
import { FUEL_TYPES, TRANSMISSIONS } from '@/features/dealer/api'
import { formatINR, statusLabel } from '@/lib/format'
import { reportError } from '@/lib/reportError'
import { catalogueAdminApi, useCatalogueMutation, type AdminModel, type AdminVariant, type VariantBody } from './catalogueApi'

const priceRule = z.number({ error: 'Enter a price.' }).positive('More than ₹0.').refine((v) => Math.round(v * 100) === v * 100, 'At most 2 decimals.')
const createSchema = z.object({
  code: z.string().trim().regex(/^[A-Z][A-Z0-9_-]{1,39}$/, '2–40 characters: upper-case letters, digits, _ and -'),
  name: z.string().trim().min(1, 'Enter a name.').max(100),
  fuelType: z.enum(FUEL_TYPES),
  transmission: z.enum(TRANSMISSIONS),
  unitPrice: priceRule,
})
type CreateValues = z.infer<typeof createSchema>

/** Variants of a model: change list price, transmission or availability, or add one. Prices are ex-GST. */
export function VariantsEditor({ model }: { model: AdminModel }) {
  const [adding, setAdding] = useState(false)
  return (
    <section aria-labelledby="variants-title" className="space-y-3">
      <div className="flex items-center justify-between">
        <h3 id="variants-title" className="text-sm font-semibold text-fg">Variants</h3>
        {!adding && (
          <Button size="sm" variant="secondary" icon={<Plus className="size-4" aria-hidden="true" />} onClick={() => setAdding(true)}>
            Add variant
          </Button>
        )}
      </div>
      {adding && <NewVariant modelId={model.id} onDone={() => setAdding(false)} />}
      {model.variants.length === 0 ? (
        <p className="text-sm text-fg-muted">No variants yet. Dealers only see models with at least one active variant.</p>
      ) : (
        <ul className="divide-y divide-line rounded-lg border border-line">
          {model.variants.map((v) => (
            <VariantRow key={`${v.id}-${v.version}`} variant={v} />
          ))}
        </ul>
      )}
    </section>
  )
}

function VariantRow({ variant }: { variant: AdminVariant }) {
  const toast = useToast()
  const [price, setPrice] = useState(String(variant.unitPrice))
  const save = useCatalogueMutation((body: VariantBody) => catalogueAdminApi.updateVariant(variant.id, body))
  const priceCheck = priceRule.safeParse(Number(price))
  const update = (body: VariantBody, done: string) =>
    save.mutate({ version: variant.version, ...body }, { onSuccess: () => toast.success(done, variant.name), onError: (e) => reportError(toast, e) })

  return (
    <li className="space-y-2 p-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="min-w-0">
          <p className="font-medium text-fg">{variant.name}</p>
          <p className="text-xs text-fg-muted">
            {variant.code} · {statusLabel(variant.fuelType)} · {formatINR(variant.unitPrice)}
          </p>
        </div>
        <Badge tone={variant.active ? 'success' : 'neutral'}>{variant.active ? 'On sale' : 'Off sale'}</Badge>
      </div>
      <div className="flex flex-wrap items-end gap-2">
        <label className="text-xs text-fg-muted">
          Transmission
          <select value={variant.transmission} disabled={save.isPending} onChange={(e) => update({ transmission: e.target.value }, 'Transmission updated')} className={inputClass}>
            {TRANSMISSIONS.map((t) => (
              <option key={t} value={t}>
                {statusLabel(t)}
              </option>
            ))}
          </select>
        </label>
        <label className="text-xs text-fg-muted">
          List price (₹)
          <input type="number" step="0.01" min="0.01" value={price} onChange={(e) => setPrice(e.target.value)} aria-invalid={!priceCheck.success} className={`${inputClass} w-36`} />
        </label>
        <Button size="sm" variant="secondary" disabled={!priceCheck.success || Number(price) === variant.unitPrice} loading={save.isPending} onClick={() => update({ unitPrice: Number(price) }, 'Price updated')}>
          Save price
        </Button>
        <Button size="sm" variant="ghost" disabled={save.isPending} onClick={() => update({ active: !variant.active }, variant.active ? 'Variant taken off sale' : 'Variant back on sale')}>
          {variant.active ? 'Take off sale' : 'Put on sale'}
        </Button>
      </div>
    </li>
  )
}

function NewVariant({ modelId, onDone }: { modelId: number; onDone: () => void }) {
  const toast = useToast()
  const create = useCatalogueMutation((body: VariantBody) => catalogueAdminApi.createVariant(modelId, body))
  const form = useForm<CreateValues>({ resolver: zodResolver(createSchema), defaultValues: { code: '', name: '', fuelType: 'PETROL', transmission: 'MANUAL' } })
  return (
    <Form
      form={form}
      aria-label="New variant"
      className="space-y-3 rounded-lg border border-line p-3"
      onSubmit={(v) => create.mutate(v, { onSuccess: (saved) => { toast.success('Variant added', saved.name); onDone() }, onError: (e) => reportError(toast, e, form) })}
    >
      <div className="grid gap-3 sm:grid-cols-2">
        <TextField name="code" label="Code" required />
        <TextField name="name" label="Name" required />
        <SelectField name="fuelType" label="Fuel" required options={FUEL_TYPES.map((f) => ({ value: f, label: statusLabel(f) }))} />
        <SelectField name="transmission" label="Transmission" required options={TRANSMISSIONS.map((t) => ({ value: t, label: statusLabel(t) }))} />
        <TextField name="unitPrice" label="List price (₹, ex-GST)" type="number" step="0.01" required />
      </div>
      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={onDone}>Cancel</Button>
        <Button type="submit" loading={create.isPending}>Add variant</Button>
      </div>
    </Form>
  )
}
