import { PanelLeftClose, PanelLeftOpen } from 'lucide-react'
import { AnimatePresence, motion } from 'motion/react'
import { useState } from 'react'
import { navFor } from '@/app/navigation'
import { useAuthUser } from '@/features/auth/useAuth'
import { cn } from '@/lib/cn'
import { fade, panelEnter } from '@/lib/motion'
import { AnimatedOutlet } from './AnimatedOutlet'
import { MobileSidebar, SidebarNav } from './Sidebar'
import { TopBar } from './TopBar'
import { Wordmark } from './Wordmark'

export function AppShell() {
  const user = useAuthUser()
  const items = navFor(user)
  const [collapsed, setCollapsed] = useState(false)
  const [mobileOpen, setMobileOpen] = useState(false)

  return (
    <div className="min-h-screen lg:flex">
      <a href="#main" className="sr-only z-50 rounded bg-accent px-3 py-2 text-charcoal focus:not-sr-only focus:fixed focus:left-2 focus:top-2">
        Skip to content
      </a>

      {/* Desktop: `layout` animates the width change with transforms (FLIP), not by animating width. */}
      <motion.aside
        layout
        transition={panelEnter}
        className={cn(
          'sticky top-0 z-40 hidden h-screen shrink-0 flex-col bg-charcoal text-white lg:flex',
          collapsed ? 'w-16' : 'w-64',
        )}
      >
        <motion.div layout="position" className="flex h-16 items-center border-b border-white/10 px-4">
          <Wordmark compact={collapsed} />
        </motion.div>
        <SidebarNav items={items} collapsed={collapsed} layoutGroup="desktop" />
        <motion.div layout="position" className="border-t border-white/10 p-2">
          <button
            type="button"
            onClick={() => setCollapsed((c) => !c)}
            aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
            aria-expanded={!collapsed}
            className="flex min-h-11 w-full items-center gap-3 rounded-lg px-3 text-sm text-gray-300 transition-colors duration-150 hover:bg-white/5 hover:text-white focus-visible:outline-2 focus-visible:outline-accent"
          >
            {collapsed ? <PanelLeftOpen className="size-5 shrink-0" aria-hidden="true" /> : <PanelLeftClose className="size-5 shrink-0" aria-hidden="true" />}
            <AnimatePresence initial={false}>
              {!collapsed && (
                <motion.span key="label" variants={fade} initial="initial" animate="animate" exit="exit">
                  Collapse
                </motion.span>
              )}
            </AnimatePresence>
          </button>
        </motion.div>
      </motion.aside>

      <MobileSidebar open={mobileOpen} onClose={() => setMobileOpen(false)} items={items} />

      <div className="flex min-w-0 flex-1 flex-col">
        <TopBar onOpenMenu={() => setMobileOpen(true)} />
        <main id="main" tabIndex={-1} className="flex-1 px-4 py-6 outline-none sm:px-6 lg:px-8">
          <AnimatedOutlet />
        </main>
      </div>
    </div>
  )
}
