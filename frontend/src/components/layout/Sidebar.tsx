import { AnimatePresence, motion } from 'motion/react'
import { NavLink } from 'react-router'
import type { NavItem } from '@/app/navigation'
import { cn } from '@/lib/cn'
import { drawerLeft, backdrop, fade } from '@/lib/motion'
import { useOverlay } from '@/components/ui/useOverlay'
import { Wordmark } from './Wordmark'

type SidebarNavProps = { items: NavItem[]; collapsed?: boolean; onNavigate?: () => void; layoutGroup: string }

export function SidebarNav({ items, collapsed = false, onNavigate, layoutGroup }: SidebarNavProps) {
  return (
    <nav aria-label="Main" className="flex-1 overflow-y-auto px-2 py-4">
      <ul className="space-y-1">
        {items.map(({ to, label, icon: Icon }) => (
          <li key={to}>
            <NavLink
              to={to}
              onClick={onNavigate}
              title={collapsed ? label : undefined}
              aria-label={collapsed ? label : undefined}
              className={({ isActive }) =>
                cn(
                  'relative flex min-h-11 items-center gap-3 rounded-lg px-3 text-sm font-medium transition-colors duration-150',
                  'focus-visible:outline-2 focus-visible:outline-accent',
                  isActive ? 'text-white' : 'text-gray-300 hover:bg-white/5 hover:text-white',
                )
              }
            >
              {({ isActive }) => (
                <>
                  {isActive && (
                    <motion.span
                      layoutId={`${layoutGroup}-active`}
                      className="absolute inset-0 rounded-lg bg-white/10 shadow-[inset_3px_0_0_var(--color-accent)]"
                      transition={{ duration: 0.2 }}
                    />
                  )}
                  <Icon className={cn('relative size-5 shrink-0', isActive && 'text-accent')} aria-hidden="true" />
                  <AnimatePresence initial={false}>
                    {!collapsed && (
                      <motion.span key="label" variants={fade} initial="initial" animate="animate" exit="exit" className="relative truncate">
                        {label}
                      </motion.span>
                    )}
                  </AnimatePresence>
                </>
              )}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  )
}

/** Under `lg`, the sidebar slides over the content with a backdrop. */
export function MobileSidebar({ open, onClose, items }: { open: boolean; onClose: () => void; items: NavItem[] }) {
  return <AnimatePresence>{open && <MobileSidebarPanel key="mobile-nav" onClose={onClose} items={items} />}</AnimatePresence>
}

function MobileSidebarPanel({ onClose, items }: { onClose: () => void; items: NavItem[] }) {
  const ref = useOverlay<HTMLDivElement>(true, onClose)
  return (
    <div className="fixed inset-0 z-40 lg:hidden">
      <motion.div variants={backdrop} initial="initial" animate="animate" exit="exit" className="absolute inset-0 bg-black/50" onClick={onClose} aria-hidden="true" />
      <motion.div
        ref={ref}
        role="dialog"
        aria-modal="true"
        aria-label="Navigation"
        tabIndex={-1}
        variants={drawerLeft}
        initial="initial"
        animate="animate"
        exit="exit"
        className="absolute inset-y-0 left-0 flex w-72 max-w-[85vw] flex-col bg-charcoal text-white shadow-xl outline-none"
      >
        <div className="flex h-16 items-center border-b border-white/10 px-4">
          <Wordmark />
        </div>
        <SidebarNav items={items} onNavigate={onClose} layoutGroup="mobile" />
      </motion.div>
    </div>
  )
}
