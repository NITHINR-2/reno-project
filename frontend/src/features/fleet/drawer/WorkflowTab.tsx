import { motion } from 'motion/react'
import { Badge } from '@/components/ui/Badge'
import { ErrorState } from '@/components/ui/ErrorState'
import { SkeletonRows } from '@/components/ui/Skeleton'
import { cn } from '@/lib/cn'
import { formatDateTime, statusLabel } from '@/lib/format'
import { listItem } from '@/lib/motion'
import type { FleetRequestDetail } from '../api'
import { useFleetPart } from '../queries'
import { Section } from './parts'

export function WorkflowTab({ request }: { request: FleetRequestDetail }) {
  const history = useFleetPart(request.id, 'history')

  return (
    <>
      <Section title="Next steps available to you">
        {request.allowedActions.length === 0 ? (
          <p className="text-sm text-fg-muted">None in the current state.</p>
        ) : (
          <div className="flex flex-wrap gap-2">
            {request.allowedActions.map((a) => (
              <Badge key={a} tone="accent">
                {statusLabel(a)}
              </Badge>
            ))}
          </div>
        )}
      </Section>
      <Section title="History">
        {history.isPending && <SkeletonRows rows={4} label="Loading history" />}
        {history.error && <ErrorState title="Couldn't load history" error={history.error} onRetry={() => void history.refetch()} />}
        {history.data && (
          <ol className="relative ml-2 border-l border-line">
            {history.data.content.map((entry, index, all) => {
              const current = index === all.length - 1
              return (
                <motion.li key={entry.id} custom={index} variants={listItem} initial="initial" animate="animate" className="relative mb-5 pl-6 last:mb-0">
                  <span
                    aria-hidden="true"
                    className={cn(
                      'absolute -left-[7px] top-1 size-3.5 rounded-full border-2 border-surface',
                      entry.override ? 'bg-red-500' : current ? 'bg-accent' : 'bg-fg-muted',
                    )}
                  />
                  {current && <span aria-hidden="true" className="absolute -left-[11px] -top-[3px] size-[22px] animate-ping rounded-full bg-accent/30 motion-reduce:animate-none" />}
                  <div className="flex flex-wrap items-center gap-2 text-sm">
                    <span className="font-medium text-fg">{statusLabel(entry.action)}</span>
                    {entry.toStatus && entry.toStatus !== entry.fromStatus && <span className="text-fg-muted">→ {statusLabel(entry.toStatus)}</span>}
                    {entry.override && <Badge tone="danger">Override</Badge>}
                  </div>
                  <p className="text-xs text-fg-muted">
                    {formatDateTime(entry.createdAt)}
                    {entry.actorName ? ` · ${entry.actorName}` : ' · System'}
                    {entry.actorRole ? ` (${statusLabel(entry.actorRole)})` : ''}
                  </p>
                  {entry.reason && <p className="mt-1 text-sm text-fg">“{entry.reason}”</p>}
                </motion.li>
              )
            })}
          </ol>
        )}
      </Section>
    </>
  )
}
