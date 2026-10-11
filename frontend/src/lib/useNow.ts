import { useSyncExternalStore } from 'react'

const TICK_MS = 60_000
let now = Date.now()
let timer: number | undefined
const listeners = new Set<() => void>()

function subscribe(listener: () => void) {
  listeners.add(listener)
  if (timer === undefined) {
    now = Date.now()
    timer = window.setInterval(() => {
      now = Date.now()
      listeners.forEach((l) => l())
    }, TICK_MS)
  }
  return () => {
    listeners.delete(listener)
    if (listeners.size === 0) {
      window.clearInterval(timer)
      timer = undefined
    }
  }
}

/** The current time, refreshed once a minute by one shared timer however many components use it. */
export function useNow(): number {
  return useSyncExternalStore(subscribe, () => now)
}
