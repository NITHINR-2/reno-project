import { useEffect, useState } from 'react'
import { inputClass } from '@/components/forms/Field'
import { DataTable, type Column } from '@/components/tables/DataTable'
import { Badge } from '@/components/ui/Badge'
import { Drawer } from '@/components/ui/Drawer'
import { formatDateTime, statusLabel } from '@/lib/format'
import type { AuditLog, AuditQuery } from './api'
import { FilterBar, Labeled } from './FilterBar'
import { useAuditLogs } from './queries'

const TEXT_FILTERS: { key: keyof AuditQuery; label: string; placeholder: string }[] = [
  { key: 'actorUsername', label: 'Actor', placeholder: 'Username' },
  { key: 'action', label: 'Action', placeholder: 'e.g. OVERRIDE' },
  { key: 'entityType', label: 'Entity type', placeholder: 'e.g. FLEET_REQUEST' },
  { key: 'entityId', label: 'Entity id', placeholder: 'e.g. 42' },
]
const DEBOUNCE_MS = 300

const columns: Column<AuditLog>[] = [
  { key: 'when', header: 'When', cell: (a) => <span className="whitespace-nowrap">{formatDateTime(a.createdAt)}</span> },
  { key: 'actor', header: 'Actor', cell: (a) => a.actorUsername ?? 'system' },
  { key: 'action', header: 'Action', cell: (a) => <Badge tone={a.action === 'OVERRIDE' ? 'danger' : 'neutral'}>{a.action}</Badge> },
  { key: 'entity', header: 'Entity', cell: (a) => `${a.entityType}${a.entityId ? ` #${a.entityId}` : ''}` },
  { key: 'reason', header: 'Reason', cell: (a) => <span className="line-clamp-1">{a.reason ?? '—'}</span>, mobile: false },
]

/** Read-only view over the append-only audit table. */
export function AuditLogTab() {
  const [draft, setDraft] = useState<AuditQuery>({ page: 0 })
  const [query, setQuery] = useState<AuditQuery>({ page: 0 })
  const [open, setOpen] = useState<AuditLog | null>(null)
  const logs = useAuditLogs(query)

  // Text filters wait for a pause in typing; dates apply at once.
  useEffect(() => {
    const timer = window.setTimeout(() => setQuery((q) => (sameFilters(q, draft) ? q : { ...draft, page: 0 })), DEBOUNCE_MS)
    return () => window.clearTimeout(timer)
  }, [draft])

  return (
    <>
      <FilterBar>
        {TEXT_FILTERS.map((f) => (
          <Labeled key={f.key} label={f.label}>
            {(id) => (
              <input id={id} value={(draft[f.key] as string | undefined) ?? ''} placeholder={f.placeholder} onChange={(e) => setDraft((d) => ({ ...d, [f.key]: e.target.value.trim() }))} className={inputClass} />
            )}
          </Labeled>
        ))}
        <Labeled label="From">{(id) => <input id={id} type="date" value={draft.from ?? ''} onChange={(e) => setDraft((d) => ({ ...d, from: e.target.value }))} className={inputClass} />}</Labeled>
        <Labeled label="To">{(id) => <input id={id} type="date" value={draft.to ?? ''} min={draft.from} onChange={(e) => setDraft((d) => ({ ...d, to: e.target.value }))} className={inputClass} />}</Labeled>
      </FilterBar>
      <DataTable
        label="Audit log"
        columns={columns}
        data={logs.data}
        getRowId={(a) => a.id}
        isLoading={logs.isPending}
        isFetching={logs.isFetching && !logs.isPending}
        error={logs.error}
        onRetry={() => void logs.refetch()}
        onPageChange={(page) => setQuery((q) => ({ ...q, page }))}
        onRowClick={setOpen}
        emptyTitle="No audit entries match"
      />
      <Drawer open={open !== null} onClose={() => setOpen(null)} title={open ? statusLabel(open.action) : ''} description={open ? `${open.entityType}${open.entityId ? ` #${open.entityId}` : ''}` : undefined}>
        {open && <AuditDetail entry={open} />}
      </Drawer>
    </>
  )
}

function sameFilters(a: AuditQuery, b: AuditQuery) {
  const { page: _a, ...fa } = a
  const { page: _b, ...fb } = b
  return JSON.stringify(fa) === JSON.stringify(fb)
}

function AuditDetail({ entry }: { entry: AuditLog }) {
  return (
    <div className="space-y-4 text-sm">
      <dl className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <div><dt className="text-xs text-fg-muted">When</dt><dd>{formatDateTime(entry.createdAt)}</dd></div>
        <div><dt className="text-xs text-fg-muted">Actor</dt><dd>{entry.actorUsername ?? 'system'}{entry.actorRole ? ` (${statusLabel(entry.actorRole)})` : ''}</dd></div>
        <div><dt className="text-xs text-fg-muted">IP address</dt><dd>{entry.ipAddress ?? '—'}</dd></div>
        <div><dt className="text-xs text-fg-muted">Reason</dt><dd>{entry.reason ?? '—'}</dd></div>
      </dl>
      {(['before', 'after'] as const).map((side) => (
        <section key={side}>
          <h3 className="mb-1 text-xs font-semibold uppercase tracking-wide text-fg-muted">{side}</h3>
          <pre className="max-h-72 overflow-auto rounded-lg bg-surface-muted p-3 font-mono text-xs">{entry[side] == null ? '—' : JSON.stringify(entry[side], null, 2)}</pre>
        </section>
      ))}
    </div>
  )
}
