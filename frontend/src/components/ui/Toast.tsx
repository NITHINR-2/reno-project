import { AlertTriangle, CheckCircle2, Info, X } from 'lucide-react'
import { AnimatePresence, motion } from 'motion/react'
import { useCallback, useMemo, useRef, useState, type ReactNode } from 'react'
import { cn } from '@/lib/cn'
import { toast as toastVariants } from '@/lib/motion'
import { ToastContext, type ToastApi, type ToastKind } from './useToast'

type ToastItem = { id: number; kind: ToastKind; title: string; description?: string }

const ICONS = { success: CheckCircle2, error: AlertTriangle, info: Info } as const
const ACCENTS: Record<ToastKind, string> = {
  success: 'text-green-600 dark:text-green-400',
  error: 'text-red-600 dark:text-red-400',
  info: 'text-blue-600 dark:text-blue-400',
}
const TIMEOUT_MS = 5000

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([])
  const nextId = useRef(1)

  const dismiss = useCallback((id: number) => setItems((current) => current.filter((t) => t.id !== id)), [])

  const push = useCallback(
    (kind: ToastKind, title: string, description?: string) => {
      const id = nextId.current++
      setItems((current) => [...current.slice(-3), { id, kind, title, description }])
      window.setTimeout(() => dismiss(id), TIMEOUT_MS)
    },
    [dismiss],
  )

  const api = useMemo<ToastApi>(
    () => ({
      success: (t, d) => push('success', t, d),
      error: (t, d) => push('error', t, d),
      info: (t, d) => push('info', t, d),
    }),
    [push],
  )

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div
        role="region"
        aria-label="Notifications"
        aria-live="polite"
        className="pointer-events-none fixed inset-x-4 top-4 z-[60] flex flex-col items-end gap-2 sm:left-auto sm:w-96"
      >
        <AnimatePresence initial={false}>
          {items.map((item) => {
            const Icon = ICONS[item.kind]
            return (
              <motion.div
                key={item.id}
                layout
                role={item.kind === 'error' ? 'alert' : 'status'}
                variants={toastVariants}
                initial="initial"
                animate="animate"
                exit="exit"
                drag="x"
                dragConstraints={{ left: 0, right: 0 }}
                dragElastic={0.6}
                onDragEnd={(_, info) => {
                  if (Math.abs(info.offset.x) > 80) dismiss(item.id)
                }}
                className="pointer-events-auto flex w-full items-start gap-3 rounded-xl border border-line bg-surface p-3 shadow-lg"
              >
                <Icon className={cn('mt-0.5 size-5 shrink-0', ACCENTS[item.kind])} aria-hidden="true" />
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-medium text-fg">{item.title}</p>
                  {item.description && <p className="mt-0.5 text-sm text-fg-muted">{item.description}</p>}
                </div>
                <button
                  type="button"
                  onClick={() => dismiss(item.id)}
                  aria-label="Dismiss notification"
                  className="-m-1 inline-flex size-8 items-center justify-center rounded-md text-fg-muted hover:bg-surface-muted"
                >
                  <X className="size-4" aria-hidden="true" />
                </button>
              </motion.div>
            )
          })}
        </AnimatePresence>
      </div>
    </ToastContext.Provider>
  )
}
