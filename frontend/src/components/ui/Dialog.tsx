import { AnimatePresence, motion } from 'motion/react'
import { useId, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { backdrop, modal } from '@/lib/motion'
import { useOverlay } from './useOverlay'

type DialogProps = {
  open: boolean
  onClose: () => void
  title: ReactNode
  description?: ReactNode
  footer?: ReactNode
  children?: ReactNode
  role?: 'dialog' | 'alertdialog'
}

/** Modal dialog: scales from 95% with a fade; animates out on close. */
export function Dialog({ open, onClose, ...panel }: DialogProps) {
  return createPortal(
    <AnimatePresence>{open && <DialogPanel key="dialog" onClose={onClose} {...panel} />}</AnimatePresence>,
    document.body,
  )
}

function DialogPanel({ onClose, title, description, footer, children, role = 'dialog' }: Omit<DialogProps, 'open'>) {
  const ref = useOverlay<HTMLDivElement>(true, onClose)
  const titleId = useId()
  const descriptionId = useId()
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center p-4 sm:items-center">
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
        role={role}
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={description ? descriptionId : undefined}
        tabIndex={-1}
        variants={modal}
        initial="initial"
        animate="animate"
        exit="exit"
        className="relative w-full max-w-md rounded-xl bg-surface p-5 shadow-xl outline-none"
      >
        <h2 id={titleId} className="text-lg font-semibold text-fg">
          {title}
        </h2>
        {description && (
          <p id={descriptionId} className="mt-1 text-sm text-fg-muted">
            {description}
          </p>
        )}
        {children && <div className="mt-4">{children}</div>}
        {footer && <div className="mt-5 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">{footer}</div>}
      </motion.div>
    </div>
  )
}
