import { Plus } from 'lucide-react'
import { useId, useState } from 'react'
import { inputClass } from '@/components/forms/Field'
import { Button } from '@/components/ui/Button'
import { useToast } from '@/components/ui/useToast'
import { reportError } from '@/lib/reportError'
import { catalogueAdminApi, useCatalogueMutation, type AdminColour, type AdminModel, type ColourBody } from './catalogueApi'

/** Paint colours offered for a model. Retired colours stay on past orders but can't be chosen again. */
export function ColoursEditor({ model }: { model: AdminModel }) {
  const toast = useToast()
  const ids = useId()
  const [name, setName] = useState('')
  const [hex, setHex] = useState('#FFFFFF')
  const [error, setError] = useState<string>()
  const create = useCatalogueMutation((body: ColourBody) => catalogueAdminApi.createColour(model.id, body))

  function add() {
    const trimmed = name.trim()
    if (!trimmed || trimmed.length > 50) {
      setError('Enter a name of up to 50 characters.')
      return
    }
    setError(undefined)
    create.mutate(
      { name: trimmed, hexCode: hex.toUpperCase() },
      {
        onSuccess: (c) => {
          toast.success('Colour added', c.name)
          setName('')
        },
        onError: (e) => reportError(toast, e),
      },
    )
  }

  return (
    <section aria-labelledby={`${ids}-title`} className="space-y-3">
      <h3 id={`${ids}-title`} className="text-sm font-semibold text-fg">Colours</h3>
      {model.colours.length > 0 && (
        <ul className="divide-y divide-line rounded-lg border border-line">
          {model.colours.map((c) => (
            <ColourRow key={`${c.id}-${c.version}`} colour={c} />
          ))}
        </ul>
      )}
      <div className="flex flex-wrap items-end gap-2">
        <div className="min-w-40 flex-1">
          <label htmlFor={`${ids}-name`} className="mb-1 block text-xs text-fg-muted">New colour name</label>
          <input id={`${ids}-name`} value={name} maxLength={50} onChange={(e) => setName(e.target.value)} aria-invalid={!!error} className={inputClass} />
        </div>
        <div>
          <label htmlFor={`${ids}-hex`} className="mb-1 block text-xs text-fg-muted">Swatch</label>
          <input id={`${ids}-hex`} type="color" value={hex} onChange={(e) => setHex(e.target.value)} className="h-11 w-16 cursor-pointer rounded-lg border border-line bg-surface p-1" />
        </div>
        <Button variant="secondary" loading={create.isPending} icon={<Plus className="size-4" aria-hidden="true" />} onClick={add}>
          Add colour
        </Button>
      </div>
      {error && <p className="text-sm text-red-600">{error}</p>}
    </section>
  )
}

function ColourRow({ colour }: { colour: AdminColour }) {
  const toast = useToast()
  const save = useCatalogueMutation((body: ColourBody) => catalogueAdminApi.updateColour(colour.id, body))
  return (
    <li className="flex items-center gap-3 px-3 py-2 text-sm">
      <span className="size-6 shrink-0 rounded-full ring-1 ring-line" style={{ backgroundColor: colour.hexCode }} aria-hidden="true" />
      <span className={colour.active ? 'flex-1 text-fg' : 'flex-1 text-fg-muted line-through'}>
        {colour.name} <span className="font-mono text-xs text-fg-muted">{colour.hexCode}</span>
      </span>
      <Button
        size="sm"
        variant="ghost"
        loading={save.isPending}
        aria-label={`${colour.active ? 'Retire' : 'Restore'} ${colour.name}`}
        onClick={() =>
          save.mutate(
            { version: colour.version, active: !colour.active },
            { onSuccess: (c) => toast.success(c.active ? 'Colour restored' : 'Colour retired', c.name), onError: (e) => reportError(toast, e) },
          )
        }
      >
        {colour.active ? 'Retire' : 'Restore'}
      </Button>
    </li>
  )
}
