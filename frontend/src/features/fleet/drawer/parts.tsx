import { Lock } from 'lucide-react'
import type { ReactNode } from 'react'

export function DetailList({ items }: { items: [label: string, value: ReactNode][] }) {
  return (
    <dl className="grid grid-cols-1 gap-x-6 gap-y-3 text-sm sm:grid-cols-2">
      {items.map(([label, value]) => (
        <div key={label} className="min-w-0">
          <dt className="text-xs text-fg-muted">{label}</dt>
          <dd className="mt-0.5 break-words text-fg">{value ?? '—'}</dd>
        </div>
      ))}
    </dl>
  )
}

export function Section({ title, actions, children }: { title: string; actions?: ReactNode; children: ReactNode }) {
  return (
    <section className="mb-6 last:mb-0">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <h3 className="text-sm font-semibold uppercase tracking-wide text-fg-muted">{title}</h3>
        {actions}
      </div>
      {children}
    </section>
  )
}

/** Explains why a tab is read-only; the server would refuse the change anyway. */
export function ReadOnlyNotice({ children }: { children: ReactNode }) {
  return (
    <p role="note" className="mb-4 flex items-start gap-2 rounded-lg bg-surface-muted px-3 py-2 text-sm text-fg-muted">
      <Lock className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
      <span>{children}</span>
    </p>
  )
}

export function Rows({ children, empty }: { children: ReactNode[]; empty: string }) {
  if (children.length === 0) return <p className="text-sm text-fg-muted">{empty}</p>
  return <ul className="divide-y divide-line rounded-lg border border-line">{children}</ul>
}
