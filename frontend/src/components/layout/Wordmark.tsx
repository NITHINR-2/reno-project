import { cn } from '@/lib/cn'

/** Text wordmark; deliberately not a real logo. */
export function Wordmark({ compact = false, className }: { compact?: boolean; className?: string }) {
  return (
    <span className={cn('flex items-center gap-2 font-semibold tracking-tight', className)}>
      <span aria-hidden="true" className="inline-flex size-8 shrink-0 items-center justify-center rounded-md bg-accent text-sm font-bold text-charcoal">
        RG
      </span>
      {!compact && (
        <span className="leading-tight">
          Renault Group
          <span className="block text-xs font-normal opacity-70">Fleet Portal</span>
        </span>
      )}
    </span>
  )
}
