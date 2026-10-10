import type { Transition, Variants } from 'motion/react'

/** Shared motion tokens. Quick and purposeful: small UI 150–200 ms, panels 250–300 ms. */
export const duration = {
  fast: 0.15,
  base: 0.2,
  panel: 0.28,
} as const

export const ease = {
  /** Entering: decelerate. */
  out: [0, 0, 0.2, 1],
  /** Leaving: accelerate. */
  in: [0.4, 0, 1, 1],
} as const

export const enter: Transition = { duration: duration.base, ease: ease.out }
export const exit: Transition = { duration: duration.fast, ease: ease.in }
export const panelEnter: Transition = { duration: duration.panel, ease: ease.out }
export const panelExit: Transition = { duration: duration.base, ease: ease.in }

/** List stagger: 30 ms per item, capped at 10 items. */
export const STAGGER = 0.03
export const STAGGER_CAP = 10
export const staggerDelay = (index: number) => Math.min(index, STAGGER_CAP) * STAGGER

export const page: Variants = {
  initial: { opacity: 0, y: 8 },
  animate: { opacity: 1, y: 0, transition: enter },
  exit: { opacity: 0, transition: exit },
}

export const fade: Variants = {
  initial: { opacity: 0 },
  animate: { opacity: 1, transition: enter },
  exit: { opacity: 0, transition: exit },
}

export const listItem: Variants = {
  initial: { opacity: 0, y: 4 },
  animate: (index: number = 0) => ({ opacity: 1, y: 0, transition: { ...enter, delay: staggerDelay(index) } }),
  exit: { opacity: 0, transition: exit },
}

export const backdrop: Variants = {
  initial: { opacity: 0 },
  animate: { opacity: 1, transition: panelEnter },
  exit: { opacity: 0, transition: panelExit },
}

export const drawerRight: Variants = {
  initial: { x: '100%' },
  animate: { x: 0, transition: panelEnter },
  exit: { x: '100%', transition: panelExit },
}

export const drawerLeft: Variants = {
  initial: { x: '-100%' },
  animate: { x: 0, transition: panelEnter },
  exit: { x: '-100%', transition: panelExit },
}

export const modal: Variants = {
  initial: { opacity: 0, scale: 0.95 },
  animate: { opacity: 1, scale: 1, transition: panelEnter },
  exit: { opacity: 0, scale: 0.95, transition: panelExit },
}

export const toast: Variants = {
  initial: { opacity: 0, x: 24 },
  animate: { opacity: 1, x: 0, transition: enter },
  exit: { opacity: 0, transition: exit },
}

export const press = { scale: 0.98 } as const
