import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { Form } from '@/components/forms/Form'
import { SelectField } from '@/components/forms/SelectField'
import { TextareaField } from '@/components/forms/TextareaField'
import { TextField } from '@/components/forms/TextField'
import { Button } from '@/components/ui/Button'
import { useToast } from '@/components/ui/useToast'
import { BODY_TYPES } from '@/features/dealer/api'
import { statusLabel } from '@/lib/format'
import { reportError } from '@/lib/reportError'
import { catalogueAdminApi, useCatalogueMutation, type AdminModel, type ModelBody } from './catalogueApi'

// Mirrors CreateVehicleModelRequest / UpdateVehicleModelRequest; the server re-validates.
const schema = z.object({
  code: z.string().trim().regex(/^[A-Z][A-Z0-9_-]{1,29}$/, '2–30 characters: upper-case letters, digits, _ and -'),
  name: z.string().trim().min(1, 'Enter a name.').max(100),
  vehicleCategory: z.string().trim().regex(/^[A-Z][A-Z0-9_]*$/, 'Upper-case letters, digits and _ (the GST category).').max(40),
  bodyType: z.string(),
  seatingCapacity: z.string().refine((v) => v === '' || (/^\d+$/.test(v) && Number(v) >= 1 && Number(v) <= 60), 'Between 1 and 60.'),
  imageUrl: z
    .string()
    .trim()
    .max(500)
    .refine((v) => v === '' || /^https:\/\/\S+$/.test(v) || /^\/[A-Za-z0-9._/-]+$/.test(v), 'An https:// URL or a /path on this site.'),
  description: z.string().max(1000),
  active: z.boolean(),
})
type Values = z.infer<typeof schema>

/** Create a model, or edit one (blank optional fields are left unchanged by the server). */
export function ModelForm({ model, onSaved }: { model?: AdminModel; onSaved: (model: AdminModel) => void }) {
  const toast = useToast()
  const save = useCatalogueMutation((body: ModelBody) => (model ? catalogueAdminApi.updateModel(model.id, body) : catalogueAdminApi.createModel(body)))
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      code: model?.code ?? '',
      name: model?.name ?? '',
      vehicleCategory: model?.vehicleCategory ?? '',
      bodyType: model?.bodyType ?? '',
      seatingCapacity: model?.seatingCapacity ? String(model.seatingCapacity) : '',
      imageUrl: model?.imageUrl ?? '',
      description: model?.description ?? '',
      active: model?.active ?? true,
    },
  })

  function toBody(v: Values): ModelBody {
    const optional = (value: string) => (value.trim() === '' ? undefined : value.trim())
    return {
      ...(model ? { version: model.version, active: v.active } : { code: v.code }),
      name: v.name,
      vehicleCategory: v.vehicleCategory,
      bodyType: optional(v.bodyType),
      seatingCapacity: v.seatingCapacity ? Number(v.seatingCapacity) : undefined,
      imageUrl: optional(v.imageUrl),
      description: optional(v.description),
    }
  }

  return (
    <Form
      form={form}
      aria-label={model ? 'Edit model' : 'New model'}
      className="space-y-4"
      onSubmit={(v) =>
        save.mutate(toBody(v), {
          onSuccess: (saved) => {
            toast.success(model ? 'Model updated' : 'Model created', saved.name)
            onSaved(saved)
          },
          onError: (e) => reportError(toast, e, form),
        })
      }
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField name="code" label="Code" required readOnly={!!model} hint={model ? 'Codes never change.' : 'e.g. DUSTER'} />
        <TextField name="name" label="Name" required />
        <TextField name="vehicleCategory" label="GST category" required hint="Key into the tax-rate table, e.g. SUV" />
        <SelectField name="bodyType" label="Body type" placeholder="Not set" options={BODY_TYPES.map((b) => ({ value: b, label: statusLabel(b) }))} />
        <TextField name="seatingCapacity" label="Seats" inputMode="numeric" />
        <TextField name="imageUrl" label="Image URL" placeholder="https://… or /cars/model.jpg" />
      </div>
      <TextareaField name="description" label="Description" rows={3} maxLength={1000} />
      {model && (
        <label className="flex min-h-11 items-center gap-2 text-sm text-fg">
          <input type="checkbox" {...form.register('active')} className="size-4" />
          On sale (inactive models are hidden from dealers)
        </label>
      )}
      <div className="flex justify-end">
        <Button type="submit" loading={save.isPending}>
          {model ? 'Save model' : 'Create model'}
        </Button>
      </div>
    </Form>
  )
}
