import { ErrorState } from '@/components/ui/ErrorState'
import { SkeletonRows } from '@/components/ui/Skeleton'
import { formatDateTime } from '@/lib/format'
import { useFleetPart } from '../queries'
import { Rows, Section } from './parts'

const fmt = (v: unknown) => (v == null || v === '' ? '—' : typeof v === 'object' ? JSON.stringify(v) : String(v))

/** Earlier overrides of this request, from the audit log (Supervisors hold AUDIT_LOG_READ). */
export function OverrideTrail({ requestId }: { requestId: number }) {
  const audit = useFleetPart(requestId, 'audit')
  if (audit.isPending) return <SkeletonRows rows={2} label="Loading override history" />
  if (audit.error) return <ErrorState title="Couldn't load the override history" error={audit.error} onRetry={() => void audit.refetch()} />

  const overrides = audit.data.content.filter((a) => a.action === 'OVERRIDE')
  return (
    <Section title="Override audit trail">
      <Rows empty="No overrides on this request yet.">
        {overrides.map((entry) => {
          const before = (entry.before ?? {}) as Record<string, unknown>
          const after = (entry.after ?? {}) as Record<string, unknown>
          const fields = Object.keys(after).filter((k) => JSON.stringify(before[k]) !== JSON.stringify(after[k]) && k !== 'version' && k !== 'updatedAt')
          return (
            <li key={entry.id} className="px-3 py-2 text-sm">
              <div className="flex flex-wrap justify-between gap-2">
                <span className="font-medium">{entry.actorUsername ?? 'system'}</span>
                <span className="text-xs text-fg-muted">{formatDateTime(entry.createdAt)}</span>
              </div>
              {entry.reason && <p className="mt-0.5">“{entry.reason}”</p>}
              {fields.length > 0 && (
                <ul className="mt-1 space-y-0.5 text-xs text-fg-muted">
                  {fields.map((f) => (
                    <li key={f}>
                      {f}: <span className="line-through">{fmt(before[f])}</span> → <span className="text-fg">{fmt(after[f])}</span>
                    </li>
                  ))}
                </ul>
              )}
            </li>
          )
        })}
      </Rows>
    </Section>
  )
}
