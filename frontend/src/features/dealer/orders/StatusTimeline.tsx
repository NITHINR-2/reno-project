import { Check, X } from 'lucide-react'
import { motion } from 'motion/react'
import { cn } from '@/lib/cn'
import { duration, listItem } from '@/lib/motion'
import type { DealerStatus } from '../api'
import { DEALER_STATUS_LABELS, PIPELINE } from '../statusMeta'

/** The simplified dealer pipeline; steps reveal in order and the current one has a gentle pulsing ring. */
export function StatusTimeline({ status, label }: { status: DealerStatus; label: string }) {
  const cancelled = status === 'CANCELLED'
  const current = PIPELINE.indexOf(status)
  return (
    <ol aria-label={label} className="flex flex-col gap-3 sm:flex-row sm:gap-0">
      {PIPELINE.map((step, index) => {
        const state = cancelled ? 'upcoming' : index < current ? 'done' : index === current ? 'current' : 'upcoming'
        return (
          <motion.li
            key={step}
            custom={index}
            variants={listItem}
            initial="initial"
            animate="animate"
            aria-current={state === 'current' ? 'step' : undefined}
            className="relative flex items-center gap-2 sm:flex-1 sm:flex-col sm:text-center"
          >
            {index > 0 && (
              <span
                aria-hidden="true"
                className={cn('absolute hidden h-0.5 sm:block sm:top-3.5 sm:right-1/2 sm:left-[-50%]', state === 'upcoming' ? 'bg-line' : 'bg-accent-strong')}
              />
            )}
            <span
              className={cn(
                'relative z-10 flex size-7 shrink-0 items-center justify-center rounded-full text-xs font-semibold',
                state === 'done' && 'bg-accent-strong text-charcoal',
                state === 'current' && 'bg-accent text-charcoal',
                state === 'upcoming' && 'border border-line bg-surface text-fg-muted',
              )}
            >
              {state === 'current' && (
                <motion.span
                  aria-hidden="true"
                  className="absolute inset-0 rounded-full ring-2 ring-accent"
                  animate={{ opacity: [0.8, 0], scale: [1, 1.6] }}
                  transition={{ duration: duration.panel * 6, repeat: Infinity, ease: 'easeOut' }}
                />
              )}
              {state === 'done' ? <Check className="size-4" aria-hidden="true" /> : index + 1}
            </span>
            <span className={cn('text-xs sm:mt-1', state === 'upcoming' ? 'text-fg-muted' : 'font-medium text-fg')}>
              {DEALER_STATUS_LABELS[step]}
              <span className="sr-only">{state === 'done' ? ' (done)' : state === 'current' ? ' (current)' : ''}</span>
            </span>
          </motion.li>
        )
      })}
      {cancelled && (
        <li aria-current="step" className="flex items-center gap-2 sm:flex-col sm:text-center">
          <span className="flex size-7 items-center justify-center rounded-full bg-red-600 text-white">
            <X className="size-4" aria-hidden="true" />
          </span>
          <span className="text-xs font-medium text-red-700 dark:text-red-300 sm:mt-1">Cancelled</span>
        </li>
      )}
    </ol>
  )
}
