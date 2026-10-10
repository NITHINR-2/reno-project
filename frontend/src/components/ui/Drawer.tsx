import { X } from 'lucide-react'
import { AnimatePresence, motion } from 'motion/react'
import { useId, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { backdrop, drawerRight } from '@/lib/motion'
import { useOverlay } from './useOverlay'

type DrawerProps = {
  open: boolean
  onClose: () => void
  title: ReactNode
  description?: ReactNode
  footer?: ReactNode
  children: ReactNode
}

/** Detail drawer: slides in from the right; full-screen on mobile, 640px on desktop. */
export function Drawer({ open, onClose, ...panel }: DrawerProps) {
  return createPortal(
    <AnimatePresence>{open && <DrawerPanel key="drawer" onClose={onClose} {...panel} />}</AnimatePresence>,
    document.body,
  )
}

function DrawerPanel({ onClose, title, description, footer, children }: Omit<DrawerProps, 'open'>) {
  const ref = useOverlay<HTMLDivElement>(true, onClose)
  const titleId = useId()
  return (
    <div className="fixed inset-0 z-50">
      <motion.div
        variants={backdrop}
        initial="initial"
        animate="animate"
        exit="exit"
        className="absolute inset-0 bg-black/40"
        onClick={onClose}
        aria-hidden="true"
      />
      <motion.div
        ref={ref}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        variants={drawerRight}
        initial="initial"
        animate="animate"
        exit="exit"
        className="absolute inset-y-0 right-0 flex w-full flex-col bg-surface shadow-xl outline-none md:w-[640px]"
      >
        <header className="flex items-start justify-between gap-3 border-b border-line px-4 py-3 sm:px-6">
          <div className="min-w-0">
            <h2 id={titleId} className="truncate text-lg font-semibold text-fg">
              {title}
            </h2>
            {description && <p className="mt-0.5 text-sm text-fg-muted">{description}</p>}
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="-mr-2 inline-flex size-11 shrink-0 items-center justify-center rounded-lg text-fg-muted transition-colors duration-150 hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-accent"
          >
            <X className="size-5" aria-hidden="true" />
          </button>
        </header>
        <div className="flex-1 overflow-y-auto px-4 py-4 sm:px-6">{children}</div>
        {footer && <footer className="flex justify-end gap-2 border-t border-line px-4 py-3 sm:px-6">{footer}</footer>}
      </motion.div>
    </div>
  )
}
