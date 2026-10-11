import { motion } from 'motion/react'
import { useId, type KeyboardEvent } from 'react'
import { cn } from '@/lib/cn'
import { duration } from '@/lib/motion'

export type TabItem<T extends string> = { id: T; label: string }

type TabsProps<T extends string> = {
  tabs: TabItem<T>[]
  active: T
  onChange: (tab: T) => void
  /** Prefix for tab/panel ids; the panel must use `${idPrefix}-panel` and `aria-labelledby={`${idPrefix}-tab-${active}`}`. */
  idPrefix: string
  label: string
  className?: string
}

/** Tab strip with a sliding underline (`layoutId`); arrow keys move between tabs. */
export function Tabs<T extends string>({ tabs, active, onChange, idPrefix, label, className }: TabsProps<T>) {
  const layoutId = useId()

  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    const step = event.key === 'ArrowRight' ? 1 : event.key === 'ArrowLeft' ? -1 : 0
    if (!step) return
    event.preventDefault()
    const index = tabs.findIndex((t) => t.id === active)
    const next = tabs[(index + step + tabs.length) % tabs.length].id
    onChange(next)
    document.getElementById(`${idPrefix}-tab-${next}`)?.focus()
  }

  return (
    <div role="tablist" aria-label={label} onKeyDown={onKeyDown} className={cn('flex overflow-x-auto border-b border-line', className)}>
      {tabs.map((tab) => {
        const selected = tab.id === active
        return (
          <button
            key={tab.id}
            id={`${idPrefix}-tab-${tab.id}`}
            type="button"
            role="tab"
            aria-selected={selected}
            aria-controls={`${idPrefix}-panel`}
            tabIndex={selected ? 0 : -1}
            onClick={() => onChange(tab.id)}
            className={cn(
              'relative min-h-11 shrink-0 px-3 text-sm font-medium transition-colors duration-150 focus-visible:outline-2 focus-visible:outline-accent',
              selected ? 'text-fg' : 'text-fg-muted hover:text-fg',
            )}
          >
            {tab.label}
            {selected && (
              <motion.span layoutId={layoutId} transition={{ duration: duration.base }} className="absolute inset-x-2 -bottom-px h-0.5 rounded-full bg-accent" />
            )}
          </button>
        )
      })}
    </div>
  )
}
