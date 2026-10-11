import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { z } from 'zod'
import { Form } from '@/components/forms/Form'
import { SelectField } from '@/components/forms/SelectField'
import { TextField } from '@/components/forms/TextField'
import { DataTable, type Column } from '@/components/tables/DataTable'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Drawer } from '@/components/ui/Drawer'
import { useToast } from '@/components/ui/useToast'
import { statusLabel } from '@/lib/format'
import { reportError } from '@/lib/reportError'
import type { SlaPolicy } from './api'
import { useSlaPolicies, useUpdateSlaPolicy } from './queries'

const warnAfter = (hours: number, pct: number) => Math.floor((hours * 60 * pct) / 100 / 60)

const columns: Column<SlaPolicy>[] = [
  { key: 'stage', header: 'Stage', cell: (p) => statusLabel(p.stage) },
  { key: 'hours', header: 'Hours allowed', className: 'tabular-nums', cell: (p) => `${p.hoursAllowed}h` },
  { key: 'warning', header: 'At risk from', className: 'tabular-nums', cell: (p) => `${p.warningThresholdPct}% (${warnAfter(p.hoursAllowed, p.warningThresholdPct)}h)` },
  { key: 'active', header: 'Status', cell: (p) => <Badge tone={p.active ? 'success' : 'neutral'}>{p.active ? 'Active' : 'Off'}</Badge> },
]

/** Supervisor-only. Calendar hours in IST; edits apply to stages entered afterwards and are audited. */
export function SlaPoliciesTab() {
  const policies = useSlaPolicies()
  const [editing, setEditing] = useState<SlaPolicy | null>(null)
  return (
    <>
      <p className="border-b border-line px-4 py-3 text-sm text-fg-muted">
        Calendar hours (IST). A change applies to requests entering the stage afterwards; running clocks keep their deadlines. Every change is audited.
      </p>
      <DataTable
        label="SLA policies"
        columns={columns}
        data={policies.data}
        getRowId={(p) => p.id}
        isLoading={policies.isPending}
        error={policies.error}
        onRetry={() => void policies.refetch()}
        onPageChange={() => undefined}
        onRowClick={setEditing}
      />
      <Drawer open={editing !== null} onClose={() => setEditing(null)} title={editing ? statusLabel(editing.stage) : ''} description="SLA policy">
        {editing && <PolicyForm key={editing.id} policy={editing} onDone={() => setEditing(null)} />}
      </Drawer>
    </>
  )
}

// Mirrors UpdateSlaPolicyRequest and the V10 CHECKs (1–8760 hours, 1–99 %).
const schema = z.object({
  hoursAllowed: z.string().regex(/^\d+$/, 'Enter whole hours.').refine((v) => +v >= 1 && +v <= 8760, 'Between 1 and 8,760 hours.'),
  warningThresholdPct: z.string().regex(/^\d+$/, 'Enter a whole percentage.').refine((v) => +v >= 1 && +v <= 99, 'Between 1 and 99%.'),
  active: z.enum(['true', 'false']),
})
type Values = z.infer<typeof schema>

function PolicyForm({ policy, onDone }: { policy: SlaPolicy; onDone: () => void }) {
  const toast = useToast()
  const update = useUpdateSlaPolicy()
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { hoursAllowed: String(policy.hoursAllowed), warningThresholdPct: String(policy.warningThresholdPct), active: String(policy.active) as 'true' | 'false' },
  })
  const [hours, pct] = useWatch({ control: form.control, name: ['hoursAllowed', 'warningThresholdPct'] })
  const preview = Number(hours) > 0 && Number(pct) > 0 ? `At risk after ${warnAfter(Number(hours), Number(pct))}h, overdue after ${Number(hours)}h.` : undefined

  return (
    <Form
      form={form}
      aria-label="Edit SLA policy"
      className="space-y-4"
      onSubmit={(v) =>
        update.mutate(
          { id: policy.id, version: policy.version, hoursAllowed: Number(v.hoursAllowed), warningThresholdPct: Number(v.warningThresholdPct), active: v.active === 'true' },
          {
            onSuccess: (saved) => {
              toast.success('SLA policy updated', `${statusLabel(saved.stage)}: ${saved.hoursAllowed}h.`)
              onDone()
            },
            onError: (e) => reportError(toast, e, form),
          },
        )
      }
    >
      <TextField name="hoursAllowed" label="Hours allowed" inputMode="numeric" required />
      <TextField name="warningThresholdPct" label="At risk from (% of hours)" inputMode="numeric" required hint={preview} />
      <SelectField name="active" label="Status" required options={[{ value: 'true', label: 'Active' }, { value: 'false', label: 'Off (no SLA for this stage)' }]} />
      <div className="flex justify-end">
        <Button type="submit" loading={update.isPending}>
          Save policy
        </Button>
      </div>
    </Form>
  )
}
