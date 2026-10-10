import { ErrorState } from '@/components/ui/ErrorState'
import { SkeletonRows } from '@/components/ui/Skeleton'
import { formatDateTime, statusLabel } from '@/lib/format'
import type { AuditEntry, FleetRequestDetail } from '../api'
import { useFleetPart } from '../queries'
import { Rows, Section } from './parts'

/** Top-level fields that differ between the before and after snapshots. */
function changedFields(entry: AuditEntry): string[] {
  const before = (entry.before ?? {}) as Record<string, unknown>
  const after = (entry.after ?? {}) as Record<string, unknown>
  if (typeof before !== 'object' || typeof after !== 'object') return []
  const keys = new Set([...Object.keys(before), ...Object.keys(after)])
  return [...keys].filter((k) => k !== 'version' && k !== 'updatedAt' && JSON.stringify(before[k]) !== JSON.stringify(after[k]))
}

export function AuditTab({ request }: { request: FleetRequestDetail }) {
  const audit = useFleetPart(request.id, 'audit')
  if (audit.isPending) return <SkeletonRows rows={4} label="Loading audit log" />
  if (audit.error) return <ErrorState title="Couldn't load the audit log" error={audit.error} onRetry={() => void audit.refetch()} />

  return (
    <Section title="Audit log">
      <Rows empty="No audit entries.">
        {audit.data.content.map((entry) => {
          const fields = changedFields(entry)
          return (
            <li key={entry.id} className="px-3 py-2 text-sm">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="font-medium">{statusLabel(entry.action)}</span>
                <span className="text-xs text-fg-muted">{formatDateTime(entry.createdAt)}</span>
              </div>
              <p className="text-xs text-fg-muted">
                {entry.actorUsername ?? 'system'}
                {entry.actorRole ? ` (${statusLabel(entry.actorRole)})` : ''}
                {fields.length > 0 ? ` · changed ${fields.join(', ')}` : ''}
              </p>
              {entry.reason && <p className="mt-1">Reason: {entry.reason}</p>}
            </li>
          )
        })}
      </Rows>
    </Section>
  )
}
