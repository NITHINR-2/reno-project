import { motion } from 'motion/react'
import { useId, type KeyboardEvent } from 'react'
import { cn } from '@/lib/cn'
import { duration } from '@/lib/motion'
import { TAB_LABELS, type DrawerTab } from '../rules'

type DrawerTabsProps = { tabs: DrawerTab[]; active: DrawerTab; onChange: (tab: DrawerTab) => void; idPrefix: string }

/** Tab strip with a sliding underline (`layoutId`); arrow keys move between tabs. */
export function DrawerTabs({ tabs, active, onChange, idPrefix }: DrawerTabsProps) {
  const layoutId = useId()

  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    const step = event.key === 'ArrowRight' ? 1 : event.key === 'ArrowLeft' ? -1 : 0
    if (!step) return
    event.preventDefault()
    const next = tabs[(tabs.indexOf(active) + step + tabs.length) % tabs.length]
    onChange(next)
    document.getElementById(`${idPrefix}-tab-${next}`)?.focus()
  }

  return (
    <div role="tablist" aria-label="Request sections" onKeyDown={onKeyDown} className="-mx-4 flex overflow-x-auto border-b border-line px-2 sm:-mx-6 sm:px-4">
      {tabs.map((tab) => {
        const selected = tab === active
        return (
          <button
            key={tab}
            id={`${idPrefix}-tab-${tab}`}
            type="button"
            role="tab"
            aria-selected={selected}
            aria-controls={`${idPrefix}-panel`}
            tabIndex={selected ? 0 : -1}
            onClick={() => onChange(tab)}
            className={cn(
              'relative min-h-11 shrink-0 px-3 text-sm font-medium transition-colors duration-150 focus-visible:outline-2 focus-visible:outline-accent',
              selected ? 'text-fg' : 'text-fg-muted hover:text-fg',
            )}
          >
            {TAB_LABELS[tab]}
            {selected && (
              <motion.span layoutId={layoutId} transition={{ duration: duration.base }} className="absolute inset-x-2 -bottom-px h-0.5 rounded-full bg-accent" />
            )}
          </button>
        )
      })}
    </div>
  )
}
