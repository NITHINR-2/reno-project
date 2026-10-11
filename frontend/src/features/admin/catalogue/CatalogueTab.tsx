import { Plus } from 'lucide-react'
import { useState } from 'react'
import { inputClass } from '@/components/forms/Field'
import { DataTable, type Column } from '@/components/tables/DataTable'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Drawer } from '@/components/ui/Drawer'
import { ErrorState } from '@/components/ui/ErrorState'
import { SkeletonRows } from '@/components/ui/Skeleton'
import { formatINR, statusLabel } from '@/lib/format'
import { FilterBar, Labeled } from '../FilterBar'
import { useAdminModel, useAdminModels, type AdminModel, type ModelQuery } from './catalogueApi'
import { ColoursEditor } from './ColoursEditor'
import { ModelForm } from './ModelForm'
import { VariantsEditor } from './VariantsEditor'

const columns: Column<AdminModel>[] = [
  { key: 'name', header: 'Model', cell: (m) => <span className="font-medium">{m.name}</span> },
  { key: 'code', header: 'Code', cell: (m) => <span className="font-mono">{m.code}</span> },
  { key: 'body', header: 'Body', cell: (m) => (m.bodyType ? statusLabel(m.bodyType) : '—'), mobile: false },
  { key: 'variants', header: 'Variants', cell: (m) => `${m.variants.filter((v) => v.active).length} / ${m.variants.length}` },
  { key: 'from', header: 'From', cell: (m) => (m.variants.length ? formatINR(Math.min(...m.variants.map((v) => v.unitPrice))) : '—'), mobile: false },
  { key: 'colours', header: 'Colours', cell: (m) => m.colours.filter((c) => c.active).length, mobile: false },
  { key: 'active', header: 'Status', cell: (m) => <Badge tone={m.active ? 'success' : 'neutral'}>{m.active ? 'On sale' : 'Hidden'}</Badge> },
]

/** Catalogue maintenance for ADMIN and SUPERVISOR: models, variants, prices and colours. Nothing is deleted. */
export function CatalogueTab() {
  const [query, setQuery] = useState<ModelQuery>({ page: 0 })
  const [selected, setSelected] = useState<number | 'new' | null>(null)
  const models = useAdminModels(query)
  const set = (patch: Partial<ModelQuery>) => setQuery((q) => ({ ...q, ...patch, page: 0 }))
  return (
    <>
      <FilterBar
        actions={
          <Button onClick={() => setSelected('new')} icon={<Plus className="size-4" aria-hidden="true" />}>
            New model
          </Button>
        }
      >
        <Labeled label="Search models">
          {(id) => <input id={id} type="search" placeholder="Name or code" value={query.q ?? ''} onChange={(e) => set({ q: e.target.value })} className={inputClass} />}
        </Labeled>
        <Labeled label="Status">
          {(id) => (
            <select id={id} value={query.active ?? ''} onChange={(e) => set({ active: e.target.value })} className={inputClass}>
              <option value="">All</option>
              <option value="true">On sale</option>
              <option value="false">Hidden</option>
            </select>
          )}
        </Labeled>
      </FilterBar>
      <DataTable
        label="Catalogue models"
        columns={columns}
        data={models.data}
        getRowId={(m) => m.id}
        isLoading={models.isPending}
        isFetching={models.isFetching && !models.isPending}
        error={models.error}
        onRetry={() => void models.refetch()}
        onPageChange={(page) => setQuery((q) => ({ ...q, page }))}
        onRowClick={(m) => setSelected(m.id)}
        emptyTitle="No models match"
      />
      <Drawer open={selected !== null} onClose={() => setSelected(null)} title={selected === 'new' ? 'New model' : 'Catalogue model'}>
        {selected === 'new' && <ModelForm onSaved={(m) => setSelected(m.id)} />}
        {typeof selected === 'number' && <ModelPanel id={selected} />}
      </Drawer>
    </>
  )
}

function ModelPanel({ id }: { id: number }) {
  const model = useAdminModel(id)
  if (model.error && !model.data) return <ErrorState title="Couldn't load the model" error={model.error} onRetry={() => void model.refetch()} />
  if (!model.data) return <SkeletonRows rows={6} label="Loading model" />
  return (
    <div className="space-y-8">
      <ModelForm key={model.data.version} model={model.data} onSaved={() => undefined} />
      <VariantsEditor model={model.data} />
      <ColoursEditor model={model.data} />
    </div>
  )
}
