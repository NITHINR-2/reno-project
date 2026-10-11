import { animate, useReducedMotion } from 'motion/react'
import { useEffect, useState } from 'react'
import { ease } from '@/lib/motion'
import { formatCount } from '@/lib/format'

/** Counts up from 0 on first load; jumps straight to the value with reduced motion. */
export function CountUp({ value, format = formatCount }: { value: number; format?: (value: number) => string }) {
  const reduce = useReducedMotion()
  const [shown, setShown] = useState(0)
  useEffect(() => {
    if (reduce) return
    const controls = animate(0, value, { duration: 0.6, ease: ease.out, onUpdate: setShown })
    return () => controls.stop()
  }, [value, reduce])
  return <>{format(reduce ? value : Number.isInteger(value) ? Math.round(shown) : shown)}</>
}
