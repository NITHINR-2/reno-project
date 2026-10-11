import { ShieldAlert } from 'lucide-react'
import { cn } from '@/lib/cn'

/** Supervisor-only switch into override mode, with a banner explaining what it does. */
export function OverrideBanner({ on, onToggle }: { on: boolean; onToggle: (on: boolean) => void }) {
  return (
    <div
      className={cn(
        'mb-4 flex flex-wrap items-start justify-between gap-3 rounded-lg border px-3 py-2 text-sm transition-colors duration-150',
        on ? 'border-red-300 bg-red-50 text-red-900 dark:border-red-800 dark:bg-red-950 dark:text-red-100' : 'border-line bg-surface-muted text-fg-muted',
      )}
    >
      <div className="flex min-w-0 items-start gap-2">
        <ShieldAlert className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
        <p>
          {on
            ? 'Override mode: changes bypass the stage’s editing rules, need a reason and are audited with old and new values. Status, quantities, stock, tax and payments cannot be overridden.'
            : 'Supervisor override is available for this request.'}
        </p>
      </div>
      <button
        type="button"
        role="switch"
        aria-checked={on}
        onClick={() => onToggle(!on)}
        className="inline-flex min-h-11 items-center gap-2 rounded-lg px-2 font-medium text-fg focus-visible:outline-2 focus-visible:outline-accent"
      >
        <span className={cn('relative inline-flex h-6 w-11 shrink-0 rounded-full transition-colors duration-150', on ? 'bg-red-600' : 'bg-gray-400')}>
          <span className={cn('absolute top-0.5 left-0.5 size-5 rounded-full bg-white shadow transition-transform duration-150', on && 'translate-x-5')} />
        </span>
        Supervisor override
      </button>
    </div>
  )
}
